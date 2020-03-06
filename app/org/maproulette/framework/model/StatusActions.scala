package org.maproulette.framework.model

import org.maproulette.framework.psql.CommonField

/**
 * @author mcuthbert
 */
object StatusActions extends CommonField {
  val FIELD_OSM_USER_ID = "osm_user_id"
  val FIELD_PROJECT_ID = "project_id"
  val FIELD_CHALLENGE_ID = "challenge_id"
  val FIELD_TASK_ID = "task_id"
  val FIELD_OLD_STATUS = "old_status"
  val FIELD_STATUS = "status"
  val FIELD_STARTED_AT = "started_at"
}
