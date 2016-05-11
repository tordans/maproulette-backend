package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}

import org.maproulette.actions._
import org.maproulette.data.DataManager
import org.maproulette.session.dal.{UserDAL, UserGroupDAL}

/**
  * Factory that contains references to all the DAL's in the system
  *
  * @author cuthbertm
  */
@Singleton
class DALManager @Inject() (tagDAL: TagDAL,
                            taskDAL: TaskDAL,
                            challengeDAL: ChallengeDAL,
                            surveyDAL: SurveyDAL,
                            projectDAL: ProjectDAL,
                            userDAL: UserDAL,
                            userGroupDAL: UserGroupDAL,
                            actionManager: ActionManager,
                            dataManager: DataManager) {
  def tag = tagDAL
  def task = taskDAL
  def challenge = challengeDAL
  def survey = surveyDAL
  def project = projectDAL
  def user = userDAL
  def userGroup = userGroupDAL
  def action = actionManager
  def data = dataManager

  def getManager(itemType:ItemType) : BaseDAL[Long, _] = {
    itemType match {
      case ProjectType() => projectDAL
      case ChallengeType() => challengeDAL
      case SurveyType() => surveyDAL
      case TaskType() => taskDAL
      case UserType() => userDAL
      case TagType() => tagDAL
    }
  }
}
