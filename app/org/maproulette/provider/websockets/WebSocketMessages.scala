// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.provider.websockets

import org.joda.time.DateTime
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import org.maproulette.models.TaskWithReview

/**
 * Defines case classes representing the various kinds of messages to be
 * transmitted via websocket, as well as helper methods for easily and
 * correctly constructing each kind of message
 *
 * @author nrotstan
 */
object WebSocketMessages {
  sealed trait Message {
    def messageType: String
  }

  // Client messages and data representations
  case class ClientMessage(messageType: String, meta: Option[JsValue], data: Option[JsValue]) extends Message
  case class SubscriptionData(subscriptionName: String)
  case class PingMessage(messageType: String)

  // Server-generated messages and data representations
  case class ServerMeta(subscriptionName: Option[String], created: DateTime=DateTime.now())
  sealed trait ServerMessage extends Message {
    val meta: ServerMeta
  }

  case class PongMessage(messageType: String, meta: ServerMeta) extends ServerMessage

  case class NotificationData(userId: Long, notificationType: Int)
  case class NotificationMessage(messageType: String, data: NotificationData, meta: ServerMeta) extends ServerMessage

  case class ReviewData(taskWithReview: TaskWithReview)
  case class ReviewMessage(messageType: String, data: ReviewData, meta: ServerMeta) extends ServerMessage

  // Public helper methods for creation of individual messages
  def pong() = PongMessage("pong", ServerMeta(None))
  def notificationNew(data: NotificationData) = createNotificationMessage("notification-new", data)
  def reviewNew(data: ReviewData) = createReviewMessage("review-new", data)
  def reviewClaimed(data: ReviewData) = createReviewMessage("review-claimed", data)
  def reviewUpdate(data: ReviewData) = createReviewMessage("review-update", data)

  // private helper methods
  private def createNotificationMessage(messageType: String, data: NotificationData) = {
    NotificationMessage(messageType, data, ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_USER + s"_${data.userId}")))
  }

  private def createReviewMessage(messageType: String, data: ReviewData) = {
    ReviewMessage(messageType, data, ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_REVIEWS)))
  }

  // Reads for client messages
  implicit val clientMessageReads: Reads[ClientMessage] = Json.reads[ClientMessage]
  implicit val subscriptionDataReads: Reads[SubscriptionData] = Json.reads[SubscriptionData]
  implicit val pingMessageReads: Reads[PingMessage] = Json.reads[PingMessage]

  // Writes for server-generated messages
  implicit val serverMetaWrites: Writes[ServerMeta] = Json.writes[ServerMeta]
  implicit val pongMessageWrites: Writes[PongMessage] = Json.writes[PongMessage]
  implicit val notificationDataWrites: Writes[NotificationData] = Json.writes[NotificationData]
  implicit val notificationMessageWrites: Writes[NotificationMessage] = Json.writes[NotificationMessage]
  implicit val reviewDataWrites: Writes[ReviewData] = Json.writes[ReviewData]
  implicit val reviewMessageWrites: Writes[ReviewMessage] = Json.writes[ReviewMessage]

  // Available subscription types
  val SUBSCRIPTION_USER = "user"        // expected to be accompanied by user id
  val SUBSCRIPTION_USERS = "users"
  val SUBSCRIPTION_REVIEWS = "reviews"
}
