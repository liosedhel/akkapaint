package modules

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import com.datastax.driver.core.Cluster
import com.google.inject.AbstractModule
import com.typesafe.config.{ Config, ConfigFactory }
import org.akkapaint.history.{ AkkaPaintDrawEventProjection, FlowForImagePerHour, FlowForImagePerMinute }

import scala.concurrent.ExecutionContext

class History {
  val akkaPaintHistoryConfig: Config = ConfigFactory.load("akkapaint-history.conf")
  val actorSystem = ActorSystem("AkkaPaintHistory", akkaPaintHistoryConfig)

  import scala.collection.JavaConverters._
  implicit val ec: ExecutionContext = actorSystem.dispatcher
  val cassandraCluster =
    Cluster.builder()
      .addContactPoints(akkaPaintHistoryConfig.getStringList("cassandra-journal.contact-points").asScala: _*)
      .build()
  implicit val session = cassandraCluster.connect("akka")

  actorSystem.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(AkkaPaintDrawEventProjection("PerMinuteHistory", akkaPaintHistoryConfig, FlowForImagePerMinute())),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(actorSystem)
    ),
    name = "PicturePerMinuteGenerator"
  )

  actorSystem.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(AkkaPaintDrawEventProjection("PerHourHistory", akkaPaintHistoryConfig, FlowForImagePerHour())),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(actorSystem)
    ),
    name = "PicturePerHourGenerator"
  )

}
class AkkaPaintModule extends AbstractModule {

  def configure() = {

    bind(classOf[History]).asEagerSingleton()
  }

}