package cryptobot.exchange.bybit.ws

import zio.json.ast.JsonCursor
import zio.json.ast.Json
import zio.json.ast.Json.{ Str, Bool, Obj }

object Cursors:

  val retMsgCursor : JsonCursor[Json, Str]  = JsonCursor.field("ret_msg").isString
  val topicCursor  : JsonCursor[Json, Str]  = JsonCursor.field("topic").isString
  val successCursor: JsonCursor[Json, Bool] = JsonCursor.field("success").isBool
  val typeCursor   : JsonCursor[Json, Str]  = JsonCursor.field("type").isString
  val dataCursor   : JsonCursor[Json, Obj]  = JsonCursor.field("data").isObject
  val updateCursor : JsonCursor[Json, Obj]  = dataCursor.field("update").isArray.element(0).isObject

  val argsCursor: JsonCursor[Json, Str] = JsonCursor.field("request").isObject.field("args").isArray.element(0).isString

  val updatedAtCursor: JsonCursor[Json, Str] = JsonCursor.field("updated_at").isString
  val lastPriceCursor: JsonCursor[Json, Str] = JsonCursor.field("last_price").isString

end Cursors
