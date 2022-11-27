package cryptobot.exchange.bybit.ws.models

import zio.{ Ref, UIO, Promise }
import scala.collection.immutable.{ Queue => ScalaQueue }

final class MessageCollector[A](ref: Ref[ScalaQueue[A]], promise: Promise[Nothing, Unit]):

  def add(a: => A, isDone: => Boolean = false): UIO[Unit] =
    ref.update(_ enqueue a) <* promise.succeed(()).when(isDone)

  def await: UIO[ScalaQueue[A]] = promise.await *> ref.get

end MessageCollector


object MessageCollector:
  
  def make[A]: UIO[MessageCollector[A]] =
    for
      ref <- Ref.make(ScalaQueue.empty[A])
      prm <- Promise.make[Nothing, Unit]
    yield new MessageCollector(ref, prm)

end MessageCollector
