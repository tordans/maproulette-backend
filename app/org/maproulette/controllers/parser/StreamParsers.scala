package org.maproulette.controllers.parser

import akka.stream.scaladsl.{Flow, Framing, Keep, Sink}
import akka.util.ByteString
import play.api.libs.json.JsValue
import play.api.libs.streams.Accumulator
import play.api.mvc.BodyParser

import scala.concurrent.Future

/**
  * @author cuthbertm
  */
object StreamParsers {

  /*val json: BodyParser[Seq[Seq[JsValue]]] = BodyParser { req =>
    val sink: Sink[ByteString, Future[Seq[Seq[JsValue]]]] = Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), 1000, allowTruncation = true))
      .map(_.utf8String.trim.split(",").toSeq)
      .toMat(Sink.fold(Seq.empty[Seq[JsValue]])(_ :+ _))(Keep.right)
    Accumulator(sink).map(Right.apply)
  }*/
}
