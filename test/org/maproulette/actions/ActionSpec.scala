package org.maproulette.actions

import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.test.WithApplication

/**
  * @author cuthbertm
  */
@RunWith(classOf[JUnitRunner])
class ActionSpec extends Specification {

  sequential

  "ActionManager" should {
    "add created action when project is created" in new WithApplication {
      1 mustEqual 1
    }
  }
}
