package cryptobot.exchange.bybit

import zio.Task
import zio.json.{ JsonEncoder, EncoderOps }
import zhttp.service.Channel
import zhttp.socket.WebSocketFrame

enum Currency:
  case USD, ETH, BTC

val currPairs: Map[String, (Currency, Currency)] =
  import Currency.*

  Map(
    "ETHUSD" -> (ETH, USD),
    "BTCUSD" -> (BTC, USD)
  )

object Extensions:

  extension (ch: Channel[WebSocketFrame])
    def sendJson[A](parsable: A, await: Boolean = false)(using JsonEncoder[A]): Task[Unit] =
      ch.writeAndFlush(WebSocketFrame.text(parsable.toJson), await)

    def sendPing(): Task[Unit] =
      ch.writeAndFlush(WebSocketFrame.text("""{"op": "ping"}"""))

    def sendToBybit(op: String, arg: String): Task[Unit] =
      ch.writeAndFlush(
        WebSocketFrame.text(s"""{"op": "$op", "args": ["$arg"]}"""),
        await = true
      )

end Extensions
