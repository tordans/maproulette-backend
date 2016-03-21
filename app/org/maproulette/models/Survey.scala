package org.maproulette.models

import play.api.data._
import play.api.data.Forms._
import com.google.inject.Inject
import org.maproulette.models.dal.ProjectDAL
import play.api.libs.json.{Reads, Json, Writes}

case class Answer(id:Long=(-1), answer:String)

/**
  * A survey is a special kind of challenge that shows you some geometry on the map and then asks
  * you a multiple choice question about the geometry. So something like, "Is this POI valid?"
  * A) No
  * B) Yes
  * C) Not Sure
  * D) Other
  *
  * The multiple choice answers can be anything that the user wants it to be.
  *
  * @author cuthbertm
  */
case class Survey(override val id: Long,
                  override val name: String,
                  override val identifier:Option[String]=None,
                  override val description:Option[String]=None,
                  parent:Long,
                  question:String,
                  answers:List[Answer]) extends ChildObject[Long, Project] {
  @Inject val projectDAL:ProjectDAL = null

  override def getParent = projectDAL.retrieveById(parent).get
}

object Survey {
  implicit val surveyWrites: Writes[Survey] = Json.writes[Survey]
  implicit val surveyReads: Reads[Survey] = Json.reads[Survey]

  implicit val answerWrites: Writes[Answer] = Json.writes[Answer]
  implicit val answerReads: Reads[Answer] = Json.reads[Answer]

  val surveyForm = Form(
    mapping(
      "id" -> longNumber,
      "name" -> nonEmptyText,
      "identifier" -> optional(text),
      "description" -> optional(text),
      "parent" -> longNumber,
      "question" -> nonEmptyText,
      "answers" -> list(
        mapping(
          "id" -> longNumber,
          "answer" -> nonEmptyText
        )(Answer.apply)(Answer.unapply)
      )
    )(Survey.apply)(Survey.unapply)
  )

  def emptySurvey(parentId:Long) = Survey(-1, "", None, None, parentId, "", List.empty)
}
