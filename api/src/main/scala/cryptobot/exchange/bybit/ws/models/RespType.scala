package cryptobot.exchange.bybit.ws.models

import cryptobot.exchange.bybit.Currency

enum RespType:
  case Pong
  case SuccessfulSub
  case InstrumentInfoResp(firstCurr: Currency, secondCurr: Currency)
  case UnsuccessfulSub(err: String)

enum SubRespType:
  case Snapshot, Delta
