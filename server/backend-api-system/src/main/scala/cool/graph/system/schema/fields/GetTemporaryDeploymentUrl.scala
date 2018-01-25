package cool.graph.system.schema.fields

import cool.graph.system.mutations.GetTemporaryDeployUrlInput
import sangria.marshalling.{CoercedScalaResultMarshaller, FromInput}
import sangria.schema._

object GetTemporaryDeploymentUrl {
  val inputFields = List(
    InputField("projectId", StringType, description = "")
  )

  implicit val fromInput = new FromInput[GetTemporaryDeployUrlInput] {
    val marshaller = CoercedScalaResultMarshaller.default

    def fromResult(node: marshaller.Node) = {
      val ad = node.asInstanceOf[Map[String, Any]]

      GetTemporaryDeployUrlInput(
        projectId = ad("projectId").asInstanceOf[String]
      )
    }
  }
}
