// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.framework.model

import org.joda.time.DateTime
import org.maproulette.data.{GroupType, ItemType}
import org.maproulette.framework.psql.CommonField
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * @author cuthbertm
  */
case class Group(
    override val id: Long,
    override val name: String,
    projectId: Long,
    groupType: Int,
    override val created: DateTime = DateTime.now(),
    override val modified: DateTime = DateTime.now()
) extends BaseObject[Long] {
  override val itemType: ItemType = GroupType()
}

object Group extends CommonField {
  implicit val writes: Writes[Group] = Json.writes[Group]
  implicit val reads: Reads[Group]   = Json.reads[Group]

  val FIELD_GROUP_TYPE     = "group_type"
  val FIELD_PROJECT_ID     = "project_id"
  val FIELD_UG_OSM_USER_ID = "osm_user_id"
  val FIELD_UG_GROUP_ID    = "group_id"

  val TYPE_SUPER_USER   = -1
  val TYPE_ADMIN        = 1
  val TYPE_WRITE_ACCESS = 2
  val TYPE_READ_ONLY    = 3
}
