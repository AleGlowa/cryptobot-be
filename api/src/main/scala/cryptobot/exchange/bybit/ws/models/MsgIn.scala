package cryptobot.exchange.bybit.ws.models

import zio.json.ast.Json.{ Obj, Bool }

final case class MsgIn(obj: Obj, topic: Topic)

object MsgIn:
  val originMsg: MsgIn = MsgIn(Obj("start" -> Bool(true)), Topic.NoTopic)
