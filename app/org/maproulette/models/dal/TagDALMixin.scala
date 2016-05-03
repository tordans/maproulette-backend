package org.maproulette.models.dal

import java.sql.Connection

import anorm._
import org.maproulette.exception.InvalidException
import org.maproulette.models.{BaseObject, Tag}
import org.maproulette.session.User

/**
  * @author cuthbertm
  */
trait TagDALMixin[T<:BaseObject[Long]] {
  this:BaseDAL[Long, T] =>

  // This is basically the table name, but removing the 's' for the column foreign key
  private lazy val name = tableName.substring(0, tableName.length - 1)
  def tagDAL:TagDAL

  /**
    * Updates the tags on the item. This maps the tag objects to the task objects in the database
    * through the use of a mapping table
    *
    * @param id The id of the item to add the tags too
    * @param tags A list of tags to add to the task
    * @param user The user executing the task
    */
  def updateItemTags(id:Long, tags:List[Long], user:User)(implicit c:Connection=null) : Unit = {
    this.retrieveById(id) match {
      case Some(item) =>
        item.hasWriteAccess(user)
        if (tags.nonEmpty) {
          withMRTransaction { implicit c =>
            val indexedValues = tags.zipWithIndex
            val rows = indexedValues.map{ case (_, i) =>
              s"({itemid_$i}, {tagid_$i})"
            }.mkString(",")
            val parameters = indexedValues.flatMap{ case(value, i) =>
              Seq(
                NamedParameter(s"itemid_$i", ParameterValue.toParameterValue(id)),
                NamedParameter(s"tagid_$i", ParameterValue.toParameterValue(value))
              )
            }

            SQL(s"INSERT INTO tags_on_$tableName (${name}_id, tag_id) VALUES " + rows)
              .on(parameters: _*)
              .execute()
          }
        }
      case None =>
        throw new InvalidException(s"""Could not add tags [${tags.mkString(",")}]. Item [$id] Not Found.""")
    }
  }

  /**
    * Deletes tags from a item. This will not delete any items or tags, it will simply sever the
    * connection between the item and tag.
    *
    * @param id The item id that the user is removing the tags from
    * @param tags The tags that are being removed from the item
    * @param user The user executing the item
    */
  def deleteItemags(id:Long, tags:List[Long], user:User)(implicit c:Connection=null) : Unit = {
    if (tags.nonEmpty) {
      withMRTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_$tableName WHERE ${name}_id = {$id} AND tag_id IN ($tags)""".execute()
      }
    }
  }

  /**
    * Pretty much the same as {@link this#deleteItemTags} but removes the tags from the items based
    * on the name of the tag instead of the id. This is most likely to be used more often.
    *
    * @param id The id of the item that is having the tags remove from it
    * @param tags The tags to be removed from the item
    * @param user The user executing the item
    */
  def deleteItemStringTags(id:Long, tags:List[String], user:User)(implicit c:Connection=null) : Unit = {
    if (tags.nonEmpty) {
      val lowerTags = tags.map(_.toLowerCase)
      withMRTransaction { implicit c =>
        SQL"""DELETE FROM tags_on_$tableName tt USING tags t
              WHERE tt.tag_id = t.id AND
                    tt.${name}_id = $id AND
                    t.name IN ($lowerTags)""".execute()
      }
    }
  }

  /**
    * Links tags to a specific item. If the tags in the provided list do not exist then it will
    * create the new tags.
    *
    * @param id The item id to update with
    * @param tags The tags to be applied to the item
    * @param user The user executing the item
    */
  def updateItemTagNames(id:Long, tags:List[String], user:User)(implicit c:Connection=null) : Unit = {
    val tagIds = tags.flatMap { tag => {
      tagDAL.retrieveByName(tag) match {
        case Some(t) => Some(t.id)
        case None => Some(tagDAL.insert(Tag(-1, tag), user).id)
      }
    }}
    updateItemTags(id, tagIds, user)
  }

  /**
    * Get a list of items based purely on the tags that are associated with the items
    *
    * @param tags The list of tags to match
    * @param limit The number of items to return
    * @param offset For paging, where 0 is the first page
    * @return A list of tags that have the tags
    */
  def getItemsBasedOnTags(tags:List[String], limit:Int, offset:Int)(implicit c:Connection=null) : List[T] = {
    val lowerTags = tags.map(_.toLowerCase)
    withMRConnection { implicit c =>
      val sqlLimit = if (limit == -1) "ALL" else limit+""
      val query = s"""SELECT $retrieveColumns FROM $tableName
                      INNER JOIN challenges c ON c.id = $tableName.parent_id
                      INNER JOIN projects p ON p.id = c.parent_id
                      INNER JOIN tags_on_$tableName tt ON $tableName.id = tt.${name}_id
                      INNER JOIN tags tg ON tg.id = tt.tag_id
                      WHERE c.enabled = TRUE AND p.enabled = TRUE AND tg.name IN ({tags})
                      LIMIT $sqlLimit OFFSET {offset}"""
      SQL(query).on('tags -> ParameterValue.toParameterValue(lowerTags), 'offset -> offset).as(parser.*)
    }
  }
}
