package controllers

import org.maproulette.controllers.CRUDController
import org.maproulette.data.Task
import org.maproulette.data.dal.{BaseDAL, TaskDAL}
import play.api.libs.json.{Reads, Writes}

/**
  * @author cuthbertm
  */
object TaskController extends CRUDController[Task] {
  override protected val dal: BaseDAL[Long, Task] = TaskDAL
  override implicit val tReads: Reads[Task] = Task.taskReads
  override implicit val tWrites: Writes[Task] = Task.taskWrites
}
