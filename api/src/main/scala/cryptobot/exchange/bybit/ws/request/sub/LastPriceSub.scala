package cryptobot.exchange.bybit.ws.request.sub

import cryptobot.exchange.bybit.Currency

final case class LastPriceSub(
  op: String,
  event: String,
  curr1: Currency,
  curr2: Currency
)
