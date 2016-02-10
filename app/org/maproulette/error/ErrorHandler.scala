package org.maproulette.error

import play.api.http.HttpErrorHandler
import play.api.mvc.{Result, RequestHeader}

import scala.concurrent.Future

/**
  * Handle errors specifically for API requests so that it returns Json
  *
  * @author cuthbertm
  */
class ErrorHandler extends HttpErrorHandler {
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = ???

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = ???
}
