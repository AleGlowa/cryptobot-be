package cryptobot.exchange.bybit.ws

import zio.json.*
import scala.util.Try

import cryptobot.exchange.bybit.Currency
import cryptobot.exchange.bybit.ws.response.LastPriceResp
import cryptobot.exchange.bybit.ws.request.sub.LastPriceSub

object Codecs:

  private given JsonCodec[Currency] = JsonCodec[String].transformOrFail ( s =>
    Try(Currency.valueOf(s)).toEither.left.map(_.toString),
    _.toString
  )

  given JsonEncoder[LastPriceResp] = DeriveJsonEncoder.gen
  given JsonDecoder[LastPriceSub] = DeriveJsonDecoder.gen[LastPriceSub].mapOrFail ( s =>
    Either.cond(s.op == "subscribe" && s.event == "lastPrice", s, "lastPrice: wrong syntax")
  )

end Codecs
