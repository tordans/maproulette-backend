package org.maproulette.framework.service

import javax.inject.{Inject, Provider, Singleton}

/**
  * Class storing references to all the services available.
  *
  * @author mcuthbert
  */
@Singleton
class ServiceManager @Inject() (
    projectService: Provider[ProjectService],
    groupService: Provider[GroupService],
    userService: Provider[UserService],
    commentService: Provider[CommentService],
    challengeService: Provider[ChallengeService],
    userMetricService: Provider[UserMetricService],
    virtualProjectService: Provider[VirtualProjectService]
) {
  def project: ProjectService = projectService.get()

  def group: GroupService = groupService.get()

  def user: UserService = userService.get()

  def comment: CommentService = commentService.get()

  def challenge: ChallengeService = challengeService.get()

  def userMetrics: UserMetricService = userMetricService.get()

  def virtualProject: VirtualProjectService = virtualProjectService.get()
}
