package cryptobot.exchange.bybit.ws.response

import java.time.Instant

import cryptobot.exchange.bybit.Currency

final case class LastPriceResp(
  curr1: Currency,
  curr2: Currency,
  lastPrice: String,
  time: Instant
)
