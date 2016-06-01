package org.maproulette.session

/**
  * This holds the search parameters that are used to define task sets for retrieving random tasks
  * from the database
  *
  * TODO add spatial filters as well
  *
  * @author cuthbertm
  */
case class SearchParameters(projectId:Option[Long]=None,
                            projectSearch:String="",
                            projectEnabled:Boolean=true,
                            challengeId:Option[Long]=None,
                            challengeTags:List[String]=List.empty,
                            challengeSearch:String="",
                            challengeEnabled:Boolean=true,
                            taskTags:List[String]=List.empty,
                            taskSearch:String="")
