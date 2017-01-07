package org.akkapaint.history

import akka.stream.alpakka.cassandra.scaladsl.CassandraSink
import com.datastax.driver.core.Session
import org.akkapaint.history.FlowForImagePerMinute.ImageUpdatePerMinute

import scala.concurrent.ExecutionContext

case class SinkAllPicturesList(implicit session: Session, executionContext: ExecutionContext) {

  val insert_statement_changes_list =
    s"""INSERT INTO changes (year, date, hour, minutes) VALUES (?,?,?,?);"""

  val sinkSaveChangesList = CassandraSink.apply[ImageUpdatePerMinute](
    8,
    session.prepare(insert_statement_changes_list),
    (data, stmt) => {
      stmt.bind(new Integer(data.date.split("-")(0)), data.date, new Integer(data.hour), new Integer(data.minute))
    }
  )

}
