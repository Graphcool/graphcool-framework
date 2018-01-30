package cool.graph.shared.logging

import cool.graph.bugsnag.{BugSnagger, GraphCoolRequest}
import cool.graph.cuid.Cuid.createCuid
import cool.graph.shared.BackendSharedMetrics

case class RequestTookVeryLongException(duration: Long) extends Exception(s"the request took very long: $duration ms")

class RequestLogger(
    requestIdPrefix: String,
    log: Function[String, Unit]
)(implicit bugsnag: BugSnagger) {
  val requestId            = requestIdPrefix + ":" + createCuid()
  var requestBeginningTime = System.currentTimeMillis()

  log(LogData(LogKey.RequestNew, requestId).json)

  val isReportLongRequestsEnabled = sys.env.get("REPORT_LONG_REQUESTS_DISABLED") match {
    case Some("1") => false
    case _         => true
  }

  def query(query: String, args: String): Unit = {
    log(
      LogData(
        key = LogKey.RequestQuery,
        requestId = requestId,
        payload = Some(Map("query" -> query, "arguments" -> args))
      ).json
    )
  }

  def end(projectId: String, clientId: Option[String], query: Option[String] = None): Unit = {
    val duration = System.currentTimeMillis() - requestBeginningTime
    val payload  = Map("request_duration" -> duration, "query" -> query.getOrElse("query not captured"))
    log(
      LogData(
        key = LogKey.RequestComplete,
        requestId = requestId,
        projectId = Some(projectId),
        clientId = clientId,
        payload = Some(payload)
      ).json)
  }
}
