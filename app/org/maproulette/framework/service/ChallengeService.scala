package org.maproulette.framework.service

import javax.inject.{Inject, Singleton}
import org.maproulette.framework.model.Challenge
import org.maproulette.framework.psql.Query
import org.maproulette.framework.repository.ChallengeRepository

/**
  * Service layer for Challenges to handle all the challenge business logic
  *
  * @author mcuthbert
  */
@Singleton
class ChallengeService @Inject() (repository: ChallengeRepository) extends ServiceMixin[Challenge] {

  def query(query: Query): List[Challenge] = this.repository.query(query)
}
