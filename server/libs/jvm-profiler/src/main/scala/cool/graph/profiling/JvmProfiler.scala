package cool.graph.profiling

import akka.actor.Cancellable
import cool.graph.metrics.MetricsManager

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

object JvmProfiler {
  def schedule(
      metricsManager: MetricsManager,
      initialDelay: FiniteDuration = 0.seconds,
      interval: FiniteDuration = 5.seconds
  ): Cancellable = {
    import metricsManager.gaugeFlushSystem.dispatcher
    val memoryProfiler = MemoryProfiler(metricsManager)
    val cpuProfiler    = CpuProfiler(metricsManager)
    metricsManager.gaugeFlushSystem.scheduler.schedule(initialDelay, interval) {
      memoryProfiler.profile()
      cpuProfiler.profile()
    }
  }
}
