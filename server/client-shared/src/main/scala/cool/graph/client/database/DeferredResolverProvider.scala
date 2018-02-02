package cool.graph.client.database

import cool.graph.client.ClientInjector
import cool.graph.client.database.DeferredTypes._
import cool.graph.metrics.ClientSharedMetrics
import sangria.execution.deferred.{Deferred, DeferredResolver}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

class DeferredResolverProvider[ConnectionOutputType, Context <: { def dataResolver: DataResolver }](
    toManyDeferredResolver: ToManyDeferredResolver[ConnectionOutputType],
    manyModelDeferredResolver: ManyModelDeferredResolver[ConnectionOutputType],
    skipPermissionCheck: Boolean = false)(implicit injector: ClientInjector)
    extends DeferredResolver[Context] {

  override def resolve(deferred: Vector[Deferred[Any]], ctx: Context, queryState: Any)(implicit ec: ExecutionContext): Vector[Future[Any]] = {

    val checkScalarFieldPermissionsDeferredResolver =
      new CheckScalarFieldPermissionsDeferredResolver(skipPermissionCheck = skipPermissionCheck, ctx.dataResolver.project)

    // group orderedDeferreds by type
    val orderedDeferred = DeferredUtils.tagDeferredByOrder(deferred)

    val manyModelDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: ManyModelDeferred[ConnectionOutputType], order) =>
        OrderedDeferred(deferred, order)
    }

    val manyModelExistsDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: ManyModelExistsDeferred, order) =>
        OrderedDeferred(deferred, order)
    }

    val countManyModelDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: CountManyModelDeferred, order) =>
        OrderedDeferred(deferred, order)
    }

    val toManyDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: ToManyDeferred[ConnectionOutputType], order) =>
        OrderedDeferred(deferred, order)
    }

    val countToManyDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: CountToManyDeferred, order) =>
        OrderedDeferred(deferred, order)
    }

    val toOneDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: ToOneDeferred, order) =>
        OrderedDeferred(deferred, order)
    }

    val checkScalarFieldPermissionsDeferreds = orderedDeferred.collect {
      case OrderedDeferred(deferred: CheckPermissionDeferred, order) =>
        OrderedDeferred(deferred, order)
    }

    // for every group, further break them down by their arguments
    val manyModelDeferredsMap                   = DeferredUtils.groupModelDeferred[ManyModelDeferred[ConnectionOutputType]](manyModelDeferreds)
    val manyModelExistsDeferredsMap             = DeferredUtils.groupModelExistsDeferred[ManyModelExistsDeferred](manyModelExistsDeferreds)
    val countManyModelDeferredsMap              = DeferredUtils.groupModelDeferred[CountManyModelDeferred](countManyModelDeferreds)
    val toManyDeferredsMap                      = DeferredUtils.groupRelatedDeferred[ToManyDeferred[ConnectionOutputType]](toManyDeferreds)
    val countToManyDeferredsMap                 = DeferredUtils.groupRelatedDeferred[CountToManyDeferred](countToManyDeferreds)
    val toOneDeferredMap                        = DeferredUtils.groupRelatedDeferred[ToOneDeferred](toOneDeferreds)
    val checkScalarFieldPermissionsDeferredsMap = DeferredUtils.groupPermissionDeferred(checkScalarFieldPermissionsDeferreds)

    // for every group of deferreds, resolve them
    val manyModelFutureResults = manyModelDeferredsMap
      .map {
        case (_, value) => manyModelDeferredResolver.resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val manyModelExistsFutureResults = manyModelExistsDeferredsMap
      .map {
        case (_, value) => new ManyModelExistsDeferredResolver().resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val countManyModelFutureResults = countManyModelDeferredsMap
      .map {
        case (_, value) => new CountManyModelDeferredResolver().resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val toManyFutureResults = toManyDeferredsMap
      .map {
        case (_, value) => toManyDeferredResolver.resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val countToManyFutureResults = countToManyDeferredsMap
      .map {
        case (_, value) => new CountToManyDeferredResolver().resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val toOneFutureResults = toOneDeferredMap
      .map {
        case (_, value) => new ToOneDeferredResolver().resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    val begin = System.currentTimeMillis()

    val checkScalarFieldPermissionsFutureResults = checkScalarFieldPermissionsDeferredsMap
      .map {
        case (_, value) => checkScalarFieldPermissionsDeferredResolver.resolve(value, ctx.dataResolver)
      }
      .toVector
      .flatten

    Future.sequence(checkScalarFieldPermissionsFutureResults.map(_.future)).onComplete { _ =>
      val duration = System.currentTimeMillis() - begin
      if (duration > 10) {
        ClientSharedMetrics.permissionCheckingTimer.record(duration, Seq(ctx.dataResolver.project.id))
      }
    }

    (manyModelFutureResults ++
      manyModelExistsFutureResults ++
      countManyModelFutureResults ++
      toManyFutureResults ++
      countToManyFutureResults ++
      toOneFutureResults ++
      checkScalarFieldPermissionsFutureResults).sortBy(_.order).map(_.future)
  }
}
