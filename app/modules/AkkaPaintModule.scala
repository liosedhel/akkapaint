package modules

import javax.inject.{ Inject, Named, Singleton }

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.cluster.singleton.{ ClusterSingletonManager, ClusterSingletonManagerSettings }
import com.datastax.driver.core.Cluster
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.{ Config, ConfigFactory }
import org.akkapaint.history.{ AkkaPaintDrawEventProjection, FlowForImagePerHour, FlowForImagePerMinute }

import scala.concurrent.ExecutionContext

@Singleton
class History @Inject() (
    @Named("AkkaPaintHistory") actorSystem: ActorSystem,
    @Named("AkkaPaintHistoryConfig") akkaPaintHistoryConfig: Config
) {

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

    val akkaPaintHistoryConfig: Config = ConfigFactory.load("akkapaint-history.conf")
    val actorSystem = ActorSystem("AkkaPaintHistory", akkaPaintHistoryConfig)

    bind(classOf[ActorSystem]).annotatedWith(Names.named("AkkaPaintHistory")).toInstance(actorSystem)
    bind(classOf[Config]).annotatedWith(Names.named("AkkaPaintHistoryConfig")).toInstance(akkaPaintHistoryConfig)
    bind(classOf[History]).asEagerSingleton()
  }

}