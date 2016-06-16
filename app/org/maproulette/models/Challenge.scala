package org.maproulette.models

import org.apache.commons.lang3.StringUtils
import play.api.data._
import play.api.data.Forms._
import org.maproulette.actions.{Actions, ChallengeType, ItemType}
import play.api.libs.json._

case class PriorityRule(operator:String, key:String, value:String) {
  def doesMatch(properties:Map[String, String]) : Boolean = {
    properties.find(pair => StringUtils.equalsIgnoreCase(pair._1, key)) match {
      case Some(v) => operator match {
        case "equal" => StringUtils.equals(v._2, value)
        case "not_equal" => !StringUtils.equals(v._2, value)
        case "contains" => StringUtils.contains(v._2, value)
        case "not_contains" => !StringUtils.contains(v._2, value)
        case "is_empty" => StringUtils.isEmpty(v._2)
        case "is_not_empty" => StringUtils.isNotEmpty(v._2)
      }
      case None => false
    }
  }
}

/**
  * The Challenge object is a child of the project object and contains Task objects as it's children.
  * It would be consider a specific problem set under a projects domain. It contains the following
  * parameters:
  *
  * id - The id assigned by the database
  * name - The name of the challenge
  * identifier - A unique identifier for the Challenge TODO: this should be removed. No real reason to keep this anymore
  * parent - The id of the project that is the parent of this challenge
  * difficulty - How difficult this challenge is consider, EASY, NORMAL or EXPERT
  * description - a brief description of the challenge
  * blurb - a quick blurb describing the problem
  * instruction - a detailed set of instructions on generally how the tasks within the challenge should be fixed
  *
  * @author cuthbertm
  */
case class Challenge(override val id: Long,
                     override val name: String,
                     override val description:Option[String]=None,
                     parent:Long,
                     instruction:String,
                     difficulty:Option[Int]=None,
                     blurb:Option[String]=None,
                     enabled:Boolean=false,
                     challengeType:Int=Actions.ITEM_TYPE_CHALLENGE,
                     featured:Boolean=false,
                     overpassQL:Option[String]=None,
                     remoteGeoJson:Option[String]=None,
                     status:Option[Int]=Some(0),
                     defaultPriority:Int=Challenge.PRIORITY_HIGH,
                     highPriorityRule:Option[String]=None,
                     mediumPriorityRule:Option[String]=None,
                     lowPriorityRule:Option[String]=None,
                     extraOptions:Option[String]=None) extends BaseObject[Long] with DefaultWrites {


  override val itemType: ItemType = ChallengeType()

  def getFriendlyStatus = {
    status match {
      case Some(status) =>
        status match {
          case Challenge.STATUS_FAILED => Challenge.STATUS_FAILED_NAME
          case Challenge.STATUS_PARTIALLY_LOADED => Challenge.STATUS_PARTIALLY_LOADED_NAME
          case Challenge.STATUS_BUILDING => Challenge.STATUS_BUILDING_NAME
          case Challenge.STATUS_COMPLETE => Challenge.STATUS_COMPLETE_NAME
          case _ => Challenge.STATUS_NA_NAME
        }
      case None => Challenge.STATUS_NA_NAME
    }
  }

  def isHighPriority(properties:Map[String, String]) : Boolean = matchesRule(highPriorityRule, properties)

  def isMediumPriority(properties:Map[String, String]) : Boolean = matchesRule(mediumPriorityRule, properties)

  def isLowRulePriority(properties:Map[String, String]) : Boolean = matchesRule(lowPriorityRule, properties)

  private def matchesRule(rule:Option[String], properties:Map[String, String]) : Boolean = {
    rule match {
      case Some(r) =>
        val ruleJSON = Json.parse(r)
        val cnf = (ruleJSON \ "condition").asOpt[String] match {
          case Some("OR") => false
          case _ => true
        }
        implicit val reads = Writes
        val rules = (ruleJSON \ "rules").as[List[JsValue]].map(jsValue => {
          val keyValue = (jsValue \ "value").as[String].split("\\.")
          PriorityRule((jsValue \ "operator").as[String], keyValue(0), keyValue(1))
        })
        val matched = rules.filter(_.doesMatch(properties))
        if (cnf && matched.size == rules.size) {
          true
        } else if (!cnf && matched.nonEmpty) {
          true
        } else {
          false
        }
      case None => false
    }
  }
}

object Challenge {
  implicit val challengeWrites: Writes[Challenge] = Json.writes[Challenge]
  implicit val challengeReads: Reads[Challenge] = Json.reads[Challenge]

  val DIFFICULTY_EASY = 1
  val DIFFICULTY_NORMAL = 2
  val DIFFICULTY_EXPERT = 3

  val PRIORITY_HIGH = 0
  val PRIORITY_MEDIUM = 1
  val PRIORITY_LOW = 2

  val challengeForm = Form(
    mapping(
      "id" -> default(longNumber, -1L),
      "name" -> nonEmptyText,
      "description" -> optional(text),
      "parent" -> longNumber,
      "instruction" -> nonEmptyText,
      "difficulty" -> optional(number(min = DIFFICULTY_EASY, max = DIFFICULTY_EXPERT+1)),
      "blurb" -> optional(text),
      "enabled" -> boolean,
      "challengeType" -> default(number, Actions.ITEM_TYPE_CHALLENGE),
      "featured" -> default(boolean, false),
      "overpassQL" -> optional(text),
      "remoteGeoJson" -> optional(text),
      "status" -> default(optional(number), None),
      "defaultPriority" -> default(number(min = PRIORITY_HIGH, max = PRIORITY_LOW+1), PRIORITY_HIGH),
      "highPriorityRule" -> optional(text),
      "mediumPriorityRule" -> optional(text),
      "lowPriorityRule" -> optional(text),
      "extraOptions" -> optional(text)
    )(Challenge.apply)(Challenge.unapply)
  )

  def emptyChallenge(parentId:Long) = Challenge(-1, "", None, parentId, "")

  val STATUS_NA = 0
  val STATUS_BUILDING = 1
  val STATUS_FAILED = 2
  val STATUS_COMPLETE = 3
  val STATUS_PARTIALLY_LOADED = 4

  val STATUS_NA_NAME = "Not Applicable"
  val STATUS_FAILED_NAME = "Failed"
  val STATUS_PARTIALLY_LOADED_NAME = "Partially Loaded"
  val STATUS_BUILDING_NAME = "Loading Tasks"
  val STATUS_COMPLETE_NAME = "Complete"
}
