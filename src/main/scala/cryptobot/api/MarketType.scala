package cryptobot.api

enum MarketType(path: String):
  case InversePerpetual extends MarketType("v2")
  case InverseFutures   extends MarketType("futures")
