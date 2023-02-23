package cryptobot.exchange.bybit.ws.model

import cryptobot.exchange.bybit.Currency

enum RespType:
  case Pong
  case SuccessfulSub
  case SuccessfulUnsub
  case InstrumentInfoResp(firstCurr: Currency, secondCurr: Currency)
  case UnsuccessfulSub(err: String)

enum SubRespType:
  case Snapshot, Delta
