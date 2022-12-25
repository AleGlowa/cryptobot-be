package cryptobot.exchange.bybit.ws.models

import zio.Duration

import Topic.Topic

final case class SubArg(topic: Topic, filters: Set[String])

object Topic extends Enumeration:
  type Topic = Value
  protected case class TopicVal(pushFreq: Duration) extends super.Val

  given Conversion[Value, TopicVal] = _.asInstanceOf[TopicVal]

  val InstrumentInfo = TopicVal(Duration.fromMillis(100))

end Topic
