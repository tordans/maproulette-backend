/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.fetchers

import org.maproulette.framework.graphql.UserContext
import org.maproulette.framework.model.Challenge
import sangria.execution.deferred.{Fetcher, Relation, RelationIds}
import org.maproulette.framework.psql.Paging
import scala.concurrent.{Future}

/**
  * @author nrotstan
  */
trait ProjectFetchers {}
object ProjectFetchers {
  val projectsFetcher = Fetcher((ctx: UserContext, ids: Seq[Long]) =>
    Future.successful(
      ctx.services.project.list(ids.toList).toSeq
    )
  )

  val fetchers = List(projectsFetcher)
}
