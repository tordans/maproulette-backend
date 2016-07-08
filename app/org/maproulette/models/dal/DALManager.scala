// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
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
  def tag:TagDAL = tagDAL
  def task:TaskDAL = taskDAL
  def challenge:ChallengeDAL = challengeDAL
  def survey:SurveyDAL = surveyDAL
  def project:ProjectDAL = projectDAL
  def user:UserDAL = userDAL
  def userGroup:UserGroupDAL = userGroupDAL
  def action:ActionManager = actionManager
  def data:DataManager = dataManager

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
