package cool.graph.system.mutations

import cool.graph.shared.database.InternalDatabase
import cool.graph.shared.functions.FunctionEnvironment
import cool.graph.shared.models._
import cool.graph.system.database.finder.ProjectQueries
import cool.graph.system.mutactions.internal.{InvalidateSchema, UpdateProject}
import cool.graph.{InternalMutation, Mutaction}
import sangria.relay.Mutation
import scaldi.{Injectable, Injector}

case class GetTemporaryUploadUrlMutation(
    client: Client,
    args: GetTemporaryDeployUrlInput,
    internalDatabase: InternalDatabase,
    project: Project
)(implicit inj: Injector)
    extends InternalMutation[GetTemporaryDeployUrlPayload]
    with Injectable {

  val projectQueries: ProjectQueries = inject[ProjectQueries](identified by "projectQueries")
  val functionEnvironment            = inject[FunctionEnvironment]
  val deploymentAccount              = pickDeploymentAccount()
  val updatedProject                 = project.copy(nextFunctionDeploymentAccount = deploymentAccount)

  override def prepareActions(): List[Mutaction] = {
    val mutactions = List(
      UpdateProject(
        client,
        project,
        updatedProject,
        internalDatabase.databaseDef,
        projectQueries
      ),
      InvalidateSchema(project)
    )

    actions = actions ++ mutactions
    actions
  }

  private def pickDeploymentAccount(): Option[String] = {
    if (!project.isEjected) {
      (project.activeFunctionDeploymentAccount, project.nextFunctionDeploymentAccount) match {
        case x @ (Some(_), _) => x._1
        case x @ (_, Some(_)) => x._2
        case _                => functionEnvironment.pickDeploymentAccount()
      }
    } else {
      functionEnvironment.pickDeploymentAccount()
    }
  }

  override def getReturnValue: Option[GetTemporaryDeployUrlPayload] = {
    val url = functionEnvironment.getTemporaryUploadUrl(updatedProject)
    Some(GetTemporaryDeployUrlPayload(url))
  }
}

case class GetTemporaryDeployUrlPayload(url: String, clientMutationId: Option[String] = None) extends Mutation
case class GetTemporaryDeployUrlInput(projectId: String)
