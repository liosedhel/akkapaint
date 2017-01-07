package org.akkapaint.shard.adapters

import akka.actor.ExtendedActorSystem
import akka.persistence.journal.{ EventAdapter, EventSeq, Tagged }
import org.akkapaint.proto.Messages.DrawEvent

class DrawEventAdapter(system: ExtendedActorSystem) extends EventAdapter {

  override def manifest(event: Any): String = event.getClass.getName

  override def toJournal(event: Any): Any = event match {
    case de: DrawEvent => Tagged(de, Set("draw_event"))
  }

  override def fromJournal(event: Any, manifest: String): EventSeq =
    EventSeq.single(event) // identity
}