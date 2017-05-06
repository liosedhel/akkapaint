package org.akkapaint.shard.actors

import akka.actor.{ ActorLogging, ActorRef, ExtendedActorSystem, Props }
import akka.persistence.{ PersistentActor, RecoveryCompleted, SnapshotOffer }
import akka.serialization.Serialization
import org.akkapaint.proto.Messages._

import scala.collection.mutable

object AkkaPaintActor {

  def props() = Props(new AkkaPaintActor())

}

class AkkaPaintActor() extends PersistentActor with ActorLogging {

  val akkaPaintBoard = mutable.Map.empty[Pixel, String]

  val registeredClients = mutable.Set.empty[ActorRef]

  var changesNumber = 0

  override def persistenceId: String = "akkaPaintActor" + self.path.toStringWithoutAddress

  override def receiveRecover: Receive = {
    case d: DrawEvent => updateState(d)
    case r: RegisterClient => registerClient(r)
    case ur: UnregisterClient => unregisterClient(ur)
    case SnapshotOffer(_, snapshot: DrawSnapshot) => {
      snapshot.changes.foreach(updateState)
      snapshot.clients.foreach(c => registerClient(RegisterClient(c)))
    }
    case RecoveryCompleted => {
      log.info(s"recovering with persistenceId $persistenceId")
      registeredClients.foreach(c => c ! ReRegisterClient())
      registeredClients.clear()
    }

  }

  override def receiveCommand: Receive = {
    case Draw(changes, color) =>
      persistAsync(new DrawEvent(changes, color)) { de =>
        updateState(de)
        changesNumber += 1
        if (changesNumber > 1000) {
          changesNumber = 0
          self ! "snap"
        }
        (registeredClients - sender())
          .foreach(_ ! Changes(de.changes, de.color))
      }
    case r: RegisterClient => persistAsync(r) { r =>
      val clientRef = registerClient(r)
      convertBoardToUpdates(akkaPaintBoard, Changes.apply).foreach(clientRef ! _)
    }
    case ur: UnregisterClient => persistAsync(ur) { ur =>
      unregisterClient(ur)
    }
    case "snap" => saveSnapshot(DrawSnapshot(
      convertBoardToUpdates(akkaPaintBoard, DrawEvent.apply).toSeq,
      registeredClients.map(Serialization.serializedActorPath).toSeq
    ))
  }

  private def updateState(drawEvent: DrawEvent) = {
    drawEvent.changes.foreach(pixel => akkaPaintBoard.put(pixel, drawEvent.color))
  }

  private def registerClient(register: RegisterClient) = {
    val clientRef = resolveActorRef(register.client)
    registeredClients.add(clientRef)
    clientRef
  }

  private def unregisterClient(unregisterClient: UnregisterClient) = {
    val clientRef = resolveActorRef(unregisterClient.client)
    registeredClients.remove(clientRef)
  }

  private def resolveActorRef(client: String): ActorRef = {
    context.system.asInstanceOf[ExtendedActorSystem].provider.resolveActorRef(client)
  }

  private def convertBoardToUpdates[T](
    akkaPaintBoard: mutable.Map[Pixel, String],
    wrap: (Seq[Pixel], String) => T
  ): Iterable[T] = {
    akkaPaintBoard.groupBy { case (changes, color) => color }.map {
      case (color, pixelMap) => wrap(pixelMap.keys.toSeq, color)
    }
  }
}
