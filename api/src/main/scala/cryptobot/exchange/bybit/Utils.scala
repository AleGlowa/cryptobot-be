package cryptobot.exchange.bybit

enum Currency:
  case USD, ETH, BTC

val currPairs: Map[String, (Currency, Currency)] =
  import Currency.*

  Map(
    "ETHUSD" -> (ETH, USD),
    "BTCUSD" -> (BTC, USD)
  )
