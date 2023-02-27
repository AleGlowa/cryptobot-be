package cryptobot.exchange.bybit.ws.model

import zio.json.ast.Json.Obj

final case class MsgIn(obj: Obj, topic: Topic)
