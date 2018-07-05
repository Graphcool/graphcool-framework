package cool.graph.shared.functions.lambda

import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import cool.graph.shared.models.Project
import play.api.libs.json.Json
import software.amazon.awssdk.auth.credentials.{AwsCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.LambdaAsyncClient

object LambdaDeploymentAccount {
  implicit val lambdaDeploymentBucket        = Json.format[LambdaDeploymentBucket]
  implicit val lambdaDeploymentAccountFormat = Json.format[LambdaDeploymentAccount]
}

case class LambdaDeploymentAccount(
    id: String,
    accessKeyID: String,
    accessKey: String,
    deployIamArn: String,
    deploymentEnabled: Boolean,
    deploymentBuckets: Vector[LambdaDeploymentBucket]
) {
  lazy val credentialsProvider = StaticCredentialsProvider.create(AwsCredentials.create(accessKeyID, accessKey))
  lazy val s3Credentials       = new BasicAWSCredentials(accessKeyID, accessKey)

  def bucket(project: Project): String = {
    val region = getRegion(project.region.toString)
    deploymentBuckets.find(_.region == region).getOrElse(sys.error("Region is not supported for lambda deployment")).deploymentBucket
  }

  def lambdaClient(project: Project): LambdaAsyncClient =
    LambdaAsyncClient
      .builder()
      .region(Region.of(project.region.toString))
      .credentialsProvider(credentialsProvider)
      .build()

  def s3Client(project: Project): AmazonS3 = {
    val region = getRegion(project.region.toString)
    AmazonS3ClientBuilder.standard
      .withCredentials(new AWSStaticCredentialsProvider(s3Credentials))
      .withEndpointConfiguration(new EndpointConfiguration(s"s3-$region.amazonaws.com", region))
      .build
  }

  private def getRegion(region: String) = Region.of(region).toString
}

case class LambdaDeploymentBucket(region: String, deploymentBucket: String)
