package cryptobot.exchange.bybit.ws.model

import cryptobot.exchange.bybit.Currency

sealed trait Topic:
  def parse: String

object Topic:

  final case class InstrumentInfo(firstCurr: Currency, secondCurr: Currency) extends Topic:
    override def parse: String = s"instrument_info.100ms.$firstCurr$secondCurr"

  case object NoTopic extends Topic:
    override def parse: String = ""

end Topic
