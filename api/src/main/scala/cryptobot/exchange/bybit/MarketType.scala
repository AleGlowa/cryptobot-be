package cryptobot.exchange.bybit

/**
 * References:
 *   - restPaths: https://bybit-exchange.github.io/docs/futuresV2/inverse/#t-ipratelimits
 *   - wssPaths : https://bybit-exchange.github.io/docs/futuresV2/inverse/#t-websocketauthentication
*/
enum MarketType(val restPath: String):
  case InversePerpetual extends MarketType("v2")
  case InverseFutures   extends MarketType("futures")

object MarketType:
  val InverseWssPath = "wss://stream.bybit.com/realtime"
