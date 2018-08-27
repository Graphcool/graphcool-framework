package cool.graph.shared.functions.lambda

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CompletableFuture, CompletionException, Semaphore}

import com.amazonaws.HttpMethod
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest
import cool.graph.cuid.Cuid
import cool.graph.shared.functions._
import cool.graph.shared.models.Project
import scalaj.http.Base64
import software.amazon.awssdk.services.lambda.model
import software.amazon.awssdk.services.lambda.model.{
  CreateFunctionRequest,
  FunctionCode,
  InvocationType,
  InvokeRequest,
  LogType,
  ResourceConflictException,
  Runtime,
  UpdateFunctionCodeRequest,
  UpdateFunctionCodeResponse,
  UpdateFunctionConfigurationRequest
}
import spray.json.{JsArray, JsObject, JsString}

import scala.compat.java8.FutureConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object LambdaFunctionEnvironment {
  def parseLambdaLogs(logs: String): Vector[JsObject] = {
    val lines = logs.split("\\n").filter(line => !line.isEmpty && !line.startsWith("START") && !line.startsWith("END") && !line.startsWith("REPORT"))

    val groupings = lines.foldLeft(Vector.empty[Vector[String]])((acc: Vector[Vector[String]], next: String) => {
      if (next.matches("\\d{4}-[01]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\.\\d+.*")) {
        acc :+ Vector(next)
      } else {
        acc.dropRight(1) :+ (acc.last :+ next)
      }
    })

    groupings.map(lineGroup => {
      val segments  = lineGroup.head.split("[\\t]", -1)
      val timeStamp = segments.head

      JsObject(timeStamp -> JsString((Vector(segments.last) ++ lineGroup.tail).mkString("\n").stripLineEnd.trim))
    })
  }
}

case class LambdaFunctionEnvironment(accounts: Vector[LambdaDeploymentAccount]) extends FunctionEnvironment {
  private val idsToAccounts        = accounts.map(a => a.id -> a).toMap
  private val maxRequestsSemaphore = new Semaphore(10)

  private def accountForId(accountId: Option[String]): LambdaDeploymentAccount =
    idsToAccounts.getOrElse(accountId.getOrElse("default"), sys.error(s"Account $accountId not configured."))

  // Picks a random account for new function deployments, ignoring disabled accounts
  override def pickDeploymentAccount(): Option[String] = {
    Random.shuffle(accounts.filter(_.deploymentEnabled)).headOption.map(_.id)
  }

  def getTemporaryUploadUrl(project: Project): String = {
    val account                     = accountForId(project.nextFunctionDeploymentAccount)
    val expiration                  = new java.util.Date()
    val oneHourFromNow              = expiration.getTime + 1000 * 60 * 60
    val generatePresignedUrlRequest = new GeneratePresignedUrlRequest(account.bucket(project), Cuid.createCuid())

    expiration.setTime(oneHourFromNow)
    generatePresignedUrlRequest.setMethod(HttpMethod.PUT)
    generatePresignedUrlRequest.setExpiration(expiration)
    account.s3Client(project).generatePresignedUrl(generatePresignedUrlRequest).toString
  }

  def deploy(project: Project, externalFile: ExternalFile, name: String): Future[DeployResponse] = {
    maxRequestsSemaphore.acquire()

    try {
      val result = deployInternal(project, externalFile, name)
      result.onComplete(_ => maxRequestsSemaphore.release())
      result
    } catch {
      case e: Throwable =>
        maxRequestsSemaphore.release()
        throw e
    }
  }

  def deployInternal(project: Project, externalFile: ExternalFile, name: String): Future[DeployResponse] = {
    val key     = externalFile.url.split("\\?").head.split("/").last
    val account = accountForId(project.nextFunctionDeploymentAccount)
    val runtime = if (!project.isEjected) "nodejs8.10" else Runtime.Nodejs610.toString

    def create =
      account
        .lambdaClient(project)
        .createFunction(
          CreateFunctionRequest.builder
            .code(FunctionCode.builder().s3Bucket(account.bucket(project)).s3Key(key).build())
            .functionName(lambdaFunctionName(project, name))
            .handler(externalFile.lambdaHandler)
            .role(account.deployIamArn)
            .timeout(15)
            .memorySize(512)
            .runtime(runtime)
            .build())
        .toScala
        .map(_ => DeploySuccess())

    def update = {
      val updateCode: CompletableFuture[UpdateFunctionCodeResponse] = account
        .lambdaClient(project)
        .updateFunctionCode(
          UpdateFunctionCodeRequest.builder
            .s3Bucket(account.bucket(project))
            .s3Key(key)
            .functionName(lambdaFunctionName(project, name))
            .build()
        )

      lazy val updateConfiguration = account
        .lambdaClient(project)
        .updateFunctionConfiguration(
          UpdateFunctionConfigurationRequest.builder
            .functionName(lambdaFunctionName(project, name))
            .handler(externalFile.lambdaHandler)
            .build()
        )

      for {
        _ <- updateCode.toScala
        _ <- updateConfiguration.toScala
      } yield DeploySuccess()
    }

    create.recoverWith {
      case e: CompletionException if e.getCause.isInstanceOf[ResourceConflictException] => update.recover { case e: Throwable => DeployFailure(e) }
      case e: Throwable                                                                 => Future.successful(DeployFailure(e))
    }
  }

  def invoke(project: Project, name: String, event: String): Future[InvokeResponse] = {
    val account = if (project.isEjected) {
      accountForId(project.activeFunctionDeploymentAccount)
    } else {
      accountForId(project.nextFunctionDeploymentAccount)
    }

    account
      .lambdaClient(project)
      .invoke(
        InvokeRequest.builder
          .functionName(lambdaFunctionName(project, name))
          .invocationType(InvocationType.RequestResponse)
          .logType(LogType.Tail) // return last 4kb of function logs
          .payload(ByteBuffer.wrap(event.getBytes("utf-8")))
          .build()
      )
      .toScala
      .map((response: model.InvokeResponse) =>
        if (response.statusCode() == 200) {
          val returnValue                = StandardCharsets.UTF_8.decode(response.payload()).toString
          val logMessage                 = Base64.decodeString(response.logResult())
          val logLines                   = LambdaFunctionEnvironment.parseLambdaLogs(logMessage)
          val returnValueWithLogEnvelope = s"""{"logs":${JsArray(logLines).compactPrint}, "response": $returnValue}"""

          response.functionError() match {
            case "Handled" | "Unhandled" => InvokeFailure(new RuntimeException(returnValueWithLogEnvelope))
            case _                       => InvokeSuccess(returnValueWithLogEnvelope)
          }
        } else {
          InvokeFailure(new RuntimeException(s"statusCode was ${response.statusCode()}"))
      })
      .recover { case e: Throwable => InvokeFailure(e) }
  }

  private def lambdaFunctionName(project: Project, functionName: String) = s"${project.id}-$functionName"
}
