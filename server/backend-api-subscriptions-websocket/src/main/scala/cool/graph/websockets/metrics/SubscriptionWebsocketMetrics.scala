package cool.graph.websockets.metrics

import cool.graph.metrics.MetricsManager
import cool.graph.profiling.JvmProfiler

object SubscriptionWebsocketMetrics extends MetricsManager {
  JvmProfiler.schedule(this)

  override def serviceName = "SubscriptionWebsocketService"

  val activeWsConnections              = defineGauge("activeWsConnections")
  val incomingWebsocketMessageRate     = defineCounter("incomingWebsocketMessageRate")
  val outgoingWebsocketMessageRate     = defineCounter("outgoingWebsocketMessageRate")
  val incomingResponseQueueMessageRate = defineCounter("incomingResponseQueueMessageRate")
}
