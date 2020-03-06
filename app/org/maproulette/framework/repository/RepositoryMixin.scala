package org.maproulette.framework.repository

import org.maproulette.framework.psql.TransactionManager
import org.maproulette.utils.{Readers, Writers}

/**
  * Handles some very basic requirements for any repository
  *
  * @author mcuthbert
  */
trait RepositoryMixin extends TransactionManager with Readers with Writers {}
