/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.mixins

import play.api.libs.json._

import org.maproulette.framework.service.{ServiceManager, TagService}
import org.maproulette.framework.model.{Challenge, Tag, Project}

import org.maproulette.utils.Utils

/**
  * ProjectMixin provides a method to insert parent project data into the JSON
  */
trait ParentProjectMixin {
  /**
    * Fetches the matching parent object and inserts it into the JSON data returned.
    *
    */
  def insertProjectJSON(serviceManager: ServiceManager, challenges: List[Challenge]): JsValue = {
    // json writes for automatically writing Challenges to a json body response
    implicit val cWrites: Writes[Challenge] = Challenge.writes.challengeWrites

    if (challenges.isEmpty) {
      Json.toJson(List[JsValue]())
    } else {
      val tags = serviceManager.tag.listByChallenges(challenges.map(c => c.id))
      val projects = Some(
        serviceManager.project
          .list(challenges.map(c => c.general.parent))
          .map(p => p.id -> p)
          .toMap
      )

      var vpIds = scala.collection.mutable.Set[Long]()
      challenges.map(c => {
        c.general.virtualParents match {
          case Some(vps) =>
            vps.map(vp => vpIds += vp)
          case _ => // do nothing
        }
      })
      val vpObjects =
        serviceManager.project.list(vpIds.toList).map(p => p.id -> p).toMap

      val jsonList = challenges.map { c =>
        var updated = Utils.insertIntoJson(
          Json.toJson(c),
          Tag.TABLE,
          Json.toJson(tags.getOrElse(c.id, List.empty).map(_.name))
        )
        val projectJson = Json
          .toJson(projects.get(c.general.parent))
          .as[JsObject] - Project.KEY_GRANTS
        updated = Utils.insertIntoJson(updated, Challenge.KEY_PARENT, projectJson, true)

        c.general.virtualParents match {
          case Some(vps) =>
            val vpJson =
              Some(vps.map(vp => Json.toJson(vpObjects.get(vp)).as[JsObject] - Project.KEY_GRANTS))
            updated = Utils.insertIntoJson(updated, Challenge.KEY_VIRTUAL_PARENTS, vpJson, true)
          case _ => // do nothing
        }
        updated
      }
      Json.toJson(jsonList)
    }
  }
}
