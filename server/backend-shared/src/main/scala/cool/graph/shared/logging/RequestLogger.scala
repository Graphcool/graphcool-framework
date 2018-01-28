package cool.graph.shared.logging

import cool.graph.bugsnag.{BugSnagger, GraphCoolRequest}
import cool.graph.cuid.Cuid.createCuid
import cool.graph.shared.BackendSharedMetrics

case class RequestTookVeryLongException(duration: Long) extends Exception(s"the request took very long: $duration ms")

class RequestLogger(
    requestIdPrefix: String,
    log: Function[String, Unit]
)(implicit bugsnag: BugSnagger) {
  val requestId: String                  = requestIdPrefix + ":" + createCuid()
  var requestBeginningTime: Option[Long] = None

  def query(query: String, args: String): Unit = {
    log(
      LogData(
        key = LogKey.RequestQuery,
        requestId = requestId,
        payload = Some(Map("query" -> query, "arguments" -> args))
      ).json
    )
  }

  def begin: String = {
    requestBeginningTime = Some(System.currentTimeMillis())
    log(LogData(LogKey.RequestNew, requestId).json)

    requestId
  }

  def end(projectId: String, clientId: Option[String], query: Option[String] = None): Unit =
    requestBeginningTime match {
      case None =>
        sys.error("you must call begin before end")

      case Some(beginTime) =>
        val duration = System.currentTimeMillis() - beginTime
        BackendSharedMetrics.requestDuration.record(duration, Seq(projectId))
        val payload = if (duration >= 2000) {
          val request = GraphCoolRequest(
            requestId = requestId,
            clientId = clientId,
            projectId = Some(projectId),
            query = query.getOrElse("not provided"),
            variables = ""
          )
          bugsnag.report(RequestTookVeryLongException(duration), request)
          Map("request_duration" -> duration, "query" -> query.getOrElse("query not captured"))
        } else {
          Map("request_duration" -> duration)
        }
        log(
          LogData(
            key = LogKey.RequestComplete,
            requestId = requestId,
            projectId = Some(projectId),
            clientId = clientId,
            payload = Some(payload)
          ).json
        )
    }
}
