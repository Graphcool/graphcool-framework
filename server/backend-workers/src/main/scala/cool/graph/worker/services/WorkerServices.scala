package cool.graph.worker.services

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cool.graph.akkautil.http.SimpleHttpClient
import cool.graph.bugsnag.BugSnagger
import cool.graph.messagebus.queue.LinearBackoff
import cool.graph.messagebus.queue.rabbit.RabbitQueue
import cool.graph.messagebus.{Queue, QueueConsumer}
import cool.graph.worker.payloads.{LogItem, Webhook}
import cool.graph.worker.utils.Env
import cool.graph.worker.workers.{FunctionLogsWorker, WebhookDelivererWorker, Worker}
import slick.jdbc.MySQLProfile

import scala.concurrent.Await
import scala.concurrent.duration._

trait WorkerServices {
  val logsDb: MySQLProfile.backend.Database
  val httpClient: SimpleHttpClient
  val logsQueue: Queue[LogItem]
  val webhooksConsumer: QueueConsumer[Webhook]
  val workers: Vector[Worker]

  def shutdown: Unit
}

case class WorkerCloudServices()(implicit system: ActorSystem, materializer: ActorMaterializer, bugsnagger: BugSnagger) extends WorkerServices {
  import cool.graph.worker.payloads.JsonConversions._
  import system.dispatcher

  lazy val httpClient = SimpleHttpClient()
  lazy val logsDb: MySQLProfile.backend.Database = {
    import slick.jdbc.MySQLProfile.api._
    Database.forConfig("logs")
  }

  lazy val webhooksConsumer: QueueConsumer[Webhook] = RabbitQueue.consumer[Webhook](Env.clusterLocalRabbitUri, "webhooks")
  lazy val logsQueue: RabbitQueue[LogItem]          = RabbitQueue[LogItem](Env.clusterLocalRabbitUri, "function-logs", LinearBackoff(5.seconds), workerConcurrency = 12)

  lazy val workers = Vector[Worker](
    FunctionLogsWorker(logsDb, logsQueue),
    WebhookDelivererWorker(httpClient, webhooksConsumer, logsQueue)
  )

  def shutdown: Unit = {
    val clientShutdown = httpClient.shutdown

    logsDb.close()
    logsQueue.shutdown
    webhooksConsumer.shutdown

    Await.result(clientShutdown, 5.seconds)
  }
}

// In the dev version the queueing impls are created / injected above the services.
case class WorkerDevServices(
    webhooksConsumer: QueueConsumer[Webhook],
    logsQueue: Queue[LogItem],
    logsDb: MySQLProfile.backend.Database
)(implicit system: ActorSystem, materializer: ActorMaterializer)
    extends WorkerServices {
  import system.dispatcher

  lazy val httpClient = SimpleHttpClient()
  lazy val workers = Vector[Worker](
    FunctionLogsWorker(logsDb, logsQueue),
    WebhookDelivererWorker(httpClient, webhooksConsumer, logsQueue)
  )

  def shutdown: Unit = {
    val clientShutdown = httpClient.shutdown

    logsDb.close()
    logsQueue.shutdown
    webhooksConsumer.shutdown

    Await.result(clientShutdown, 5.seconds)
  }
}
