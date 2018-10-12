package org.maproulette.models;

/**
 * @author mcuthbert
 */
case class MapillaryServerInfo(host:String, clientId:String, border:Double)

case class MapillaryImage(key:String,
                          lat:Double,
                          lon:Double,
                          url_320:String,
                          url_640:String,
                          url_1024:String,
                          url_2048:String)
