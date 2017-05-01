package modules

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import com.google.inject.AbstractModule
import com.typesafe.config.{ Config, ConfigFactory }
import org.akkapaint.history.AkkaPaintDrawEventProjection

class History {
  val akkaPaintHistoryConfig: Config = ConfigFactory.load("akkapaint-history.conf")
  val actorSystem = ActorSystem("AkkaPaintHistory", akkaPaintHistoryConfig)

  println("DDDDDDDDDD")

  actorSystem.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(classOf[AkkaPaintDrawEventProjection], akkaPaintHistoryConfig),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(actorSystem)
    ),
    name = "HistoryGenerator"
  )
}
class AkkaPaintModule extends AbstractModule {

  def configure() = {

    bind(classOf[History]).asEagerSingleton()
  }

}