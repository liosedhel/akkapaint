package org.akkapaint.history

import java.sql.ResultSet

import akka.NotUsed
import akka.stream.SinkShape
import com.google.common.util.concurrent.{ FutureCallback, Futures, ListenableFuture }

import scala.concurrent.{ Future, Promise }

object GuavaFutures {
  implicit final class GuavaFutureOpts[A](val guavaFut: ListenableFuture[A]) extends AnyVal {
    def asScala(): Future[A] = {
      val p = Promise[A]()
      val callback = new FutureCallback[A] {
        override def onSuccess(a: A): Unit = p.success(a)
        override def onFailure(err: Throwable): Unit = p.failure(err)
      }
      Futures.addCallback(guavaFut, callback)
      p.future
    }
  }
}

import akka.Done
import akka.stream.scaladsl.{ Flow, Sink }
import com.datastax.driver.core.{ BoundStatement, PreparedStatement, Session }
import org.akkapaint.history.GuavaFutures._

import scala.concurrent.{ ExecutionContext, Future }

object CassandraFlow {
  def apply[T](
    parallelism: Int,
    statement: PreparedStatement,
    statementBinder: (T, PreparedStatement) => BoundStatement,
    sink: Sink[SinkShape[(T, ResultSet)], Future[Done]] = Sink.ignore
  )(implicit session: Session, ex: ExecutionContext): Flow[T, T, NotUsed] =
    Flow[T]
      .mapAsyncUnordered(parallelism)(t ⇒ session.executeAsync(statementBinder(t, statement)).asScala().map(_ => t))
}

