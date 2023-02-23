package cryptobot.exchange.bybit.ws.response

import cryptobot.exchange.bybit.Currency

final case class UnsubResp(
  unsub: Boolean,
  event: String,
  args: List[Currency]
)
