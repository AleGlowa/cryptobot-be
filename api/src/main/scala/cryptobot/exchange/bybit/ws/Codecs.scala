package cryptobot.exchange.bybit.ws

import zio.json.{ JsonEncoder, DeriveJsonEncoder }

import cryptobot.exchange.bybit.Currency
import cryptobot.exchange.bybit.ws.response.LastPriceResp

object Codecs:

  private given JsonEncoder[Currency] = JsonEncoder[String].contramap(_.toString)
  given JsonEncoder[LastPriceResp] = DeriveJsonEncoder.gen

end Codecs
