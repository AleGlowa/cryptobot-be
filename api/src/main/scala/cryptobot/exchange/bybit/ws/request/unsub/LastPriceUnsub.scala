package cryptobot.exchange.bybit.ws.request.unsub

import cryptobot.exchange.bybit.Currency

final case class LastPriceUnsub(
  op: String,
  event: String,
  curr1: Currency,
  curr2: Currency
)
