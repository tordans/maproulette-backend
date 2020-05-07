/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import play.api.libs.json.JsValue

import org.maproulette.exception.NotFoundException
import org.maproulette.framework.model._
import org.maproulette.framework.psql.{Query, _}
import org.maproulette.framework.psql.filter.{BaseParameter, _}
import org.maproulette.framework.repository.ChallengeSnapshotRepository
import org.maproulette.cache.CacheManager
import org.maproulette.permissions.Permission
import org.maproulette.data.{SnapshotManager, Snapshot}
import org.maproulette.models.dal.ChallengeDAL

/**
  * Service layer for Challenge Snapshots
  *
  * @author krotstan
  */
@Singleton
class ChallengeSnapshotService @Inject() (
    repository: ChallengeSnapshotRepository,
    challengeDAL: ChallengeDAL,
    snapshotManager: SnapshotManager,
    permission: Permission
) {

  /**
    * Deletes a challenge snapshot
    *
    * @param snapshotId  The snapshot id to delete
    * @param user  The user making the request
    */
  def delete(snapshotId: Long, user: User): Unit = {
    val snapshot  = this.snapshotManager.getChallengeSnapshot(snapshotId)
    val challenge = this.challengeDAL.retrieveById(snapshot.itemId)
    this.permission.hasObjectWriteAccess(challenge.get, user)
    this.repository.delete(snapshotId)
  }
}
