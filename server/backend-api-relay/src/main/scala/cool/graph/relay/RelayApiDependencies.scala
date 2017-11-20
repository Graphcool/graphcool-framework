package cool.graph.relay

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import cool.graph.aws.cloudwatch.CloudwatchMock
import cool.graph.client.database.{DeferredResolverProvider, RelayManyModelDeferredResolver, RelayToManyDeferredResolver}
import cool.graph.client.finder.{CachedProjectFetcherImpl, ProjectFetcherImpl, RefreshableProjectFetcher}
import cool.graph.client.metrics.ApiMetricsMiddleware
import cool.graph.client.server.{GraphQlRequestHandler, GraphQlRequestHandlerImpl, ProjectSchemaBuilder}
import cool.graph.client.{CommonClientDependencies, FeatureMetric, FeatureMetricActor, UserContext}
import cool.graph.messagebus.Conversions.{ByteUnmarshaller, Unmarshallers}
import cool.graph.messagebus.pubsub.rabbit.{RabbitAkkaPubSub, RabbitAkkaPubSubSubscriber}
import cool.graph.messagebus.queue.rabbit.RabbitQueue
import cool.graph.messagebus.{Conversions, PubSubPublisher, QueuePublisher}
import cool.graph.relay.schema.RelaySchemaBuilder
import cool.graph.shared.database.GlobalDatabaseManager
import cool.graph.shared.externalServices.{DummyKinesisPublisher, KinesisPublisher}
import cool.graph.shared.functions.lambda.{LambdaFunctionEnvironment, SingleRegionBucketResolver}
import cool.graph.shared.functions.{EndpointResolver, FunctionEnvironment, LiveEndpointResolver}
import cool.graph.webhook.Webhook

import scala.util.Try

trait RelayApiClientDependencies extends CommonClientDependencies {
  import system.dispatcher

  val relayDeferredResolver: DeferredResolverProvider[_, UserContext] =
    new DeferredResolverProvider(new RelayToManyDeferredResolver, new RelayManyModelDeferredResolver)

  val relayProjectSchemaBuilder = ProjectSchemaBuilder(project => new RelaySchemaBuilder(project).build())

  val relayGraphQlRequestHandler = GraphQlRequestHandlerImpl(
    errorHandlerFactory = errorHandlerFactory,
    log = log,
    apiVersionMetric = FeatureMetric.ApiRelay,
    apiMetricsMiddleware = apiMetricsMiddleware,
    deferredResolver = relayDeferredResolver
  )

  bind[GraphQlRequestHandler] identifiedBy "relay-gql-request-handler" toNonLazy relayGraphQlRequestHandler
  bind[ProjectSchemaBuilder] identifiedBy "relay-schema-builder" toNonLazy relayProjectSchemaBuilder
}

case class RelayApiDependencies(implicit val system: ActorSystem, val materializer: ActorMaterializer) extends RelayApiClientDependencies {
  lazy val projectSchemaInvalidationSubscriber: RabbitAkkaPubSubSubscriber[String] = {
    val globalRabbitUri                                 = sys.env("GLOBAL_RABBIT_URI")
    implicit val unmarshaller: ByteUnmarshaller[String] = Unmarshallers.ToString

    RabbitAkkaPubSub.subscriber[String](globalRabbitUri, "project-schema-invalidation", durable = true)
  }

  lazy val blockedProjectIds: Vector[String] = Try {
    sys.env("BLOCKED_PROJECT_IDS").split(",").toVector
  }.getOrElse(Vector.empty)

  lazy val projectSchemaFetcher: RefreshableProjectFetcher = CachedProjectFetcherImpl(
    projectFetcher = ProjectFetcherImpl(blockedProjectIds, config),
    projectSchemaInvalidationSubscriber = projectSchemaInvalidationSubscriber
  )

  lazy val lambdaAccessKeyId = sys.env.getOrElse("LAMBDA_AWS_ACCESS_KEY_ID", "whatever")
  lazy val lambdaAccessKey   = sys.env.getOrElse("LAMBDA_AWS_SECRET_ACCESS_KEY", "whatever")
  lazy val awsRegion         = sys.env.getOrElse("AWS_REGION", "whatever")

  lazy val functionEnvironment = LambdaFunctionEnvironment(
    lambdaAccessKeyId,
    lambdaAccessKey,
    SingleRegionBucketResolver(lambdaAccessKeyId, lambdaAccessKey, awsRegion)
  )

  lazy val clusterLocalRabbitUri              = sys.env("RABBITMQ_URI")
  lazy val globalDatabaseManager              = GlobalDatabaseManager.initializeForSingleRegion(config)
  lazy val fromStringMarshaller               = Conversions.Marshallers.FromString
  lazy val endpointResolver                   = LiveEndpointResolver()
  lazy val logsPublisher                      = RabbitQueue.publisher[String](clusterLocalRabbitUri, "function-logs")(bugSnagger, fromStringMarshaller)
  lazy val webhooksPublisher                  = RabbitQueue.publisher(clusterLocalRabbitUri, "webhooks")(bugSnagger, Webhook.marshaller)
  lazy val sssEventsPublisher                 = RabbitAkkaPubSub.publisher[String](clusterLocalRabbitUri, "sss-events", durable = true)(bugSnagger, fromStringMarshaller)
  lazy val requestPrefix                      = sys.env.getOrElse("AWS_REGION", sys.error("AWS Region not found."))
  lazy val cloudwatch                         = CloudwatchMock
  lazy val kinesisAlgoliaSyncQueriesPublisher = DummyKinesisPublisher()
  lazy val kinesisApiMetricsPublisher         = DummyKinesisPublisher()
  lazy val featureMetricActor                 = system.actorOf(Props(new FeatureMetricActor(kinesisApiMetricsPublisher, apiMetricsFlushInterval)))
  lazy val apiMetricsMiddleware               = new ApiMetricsMiddleware(testableTime, featureMetricActor)

  binding identifiedBy "project-schema-fetcher" toNonLazy projectSchemaFetcher
  binding identifiedBy "cloudwatch" toNonLazy cloudwatch
  binding identifiedBy "api-metrics-middleware" toNonLazy new ApiMetricsMiddleware(testableTime, featureMetricActor)
  binding identifiedBy "featureMetricActor" to featureMetricActor

  bind[GlobalDatabaseManager] toNonLazy globalDatabaseManager
  bind[FunctionEnvironment] toNonLazy functionEnvironment
  bind[EndpointResolver] identifiedBy "endpointResolver" toNonLazy endpointResolver
  bind[QueuePublisher[String]] identifiedBy "logsPublisher" toNonLazy logsPublisher
  bind[QueuePublisher[Webhook]] identifiedBy "webhookPublisher" toNonLazy webhooksPublisher
  bind[PubSubPublisher[String]] identifiedBy "sss-events-publisher" toNonLazy sssEventsPublisher
  bind[String] identifiedBy "request-prefix" toNonLazy requestPrefix
  bind[KinesisPublisher] identifiedBy "kinesisAlgoliaSyncQueriesPublisher" toNonLazy kinesisAlgoliaSyncQueriesPublisher
  bind[KinesisPublisher] identifiedBy "kinesisApiMetricsPublisher" toNonLazy kinesisApiMetricsPublisher

}
