package org.maproulette.models.dal

import javax.inject.{Inject, Singleton}

import org.maproulette.actions.ActionManager
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
}
