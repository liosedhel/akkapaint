package modules

import javax.inject.{Inject, Named, Singleton}

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import com.datastax.driver.core.Cluster
import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory}
import org.akkapaint.history.{AkkaPaintDrawEventProjection, FlowForImagePerHour, FlowForImagePerMinute}

import scala.concurrent.ExecutionContext

@Singleton
class History @Inject()(
  @Named("AkkaPaintHistory") akkaPaintHistorySystem: ActorSystem,
  @Named("AkkaPaintHistoryConfig") akkaPaintHistoryConfig: Config
) {

  import scala.collection.JavaConverters._
  implicit val ec: ExecutionContext = akkaPaintHistorySystem.dispatcher

  val cassandraCluster =
    Cluster
      .builder()
      .addContactPoints(akkaPaintHistoryConfig.getStringList("cassandra-journal.contact-points").asScala: _*)
      .build()
  implicit val session = cassandraCluster.connect("akka_history")

  akkaPaintHistorySystem.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(
        AkkaPaintDrawEventProjection(
          persistenceId = "PerMinuteHistory",
          akkaPaintHistoryConfig = akkaPaintHistoryConfig,
          generateConsumer = FlowForImagePerMinute()
        )
      ),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(akkaPaintHistorySystem)
    ),
    name = "PicturePerMinuteGenerator"
  )

  akkaPaintHistorySystem.actorOf(
    ClusterSingletonManager.props(
      singletonProps = Props(
        AkkaPaintDrawEventProjection(
          persistenceId = "PerHourHistory",
          akkaPaintHistoryConfig = akkaPaintHistoryConfig,
          generateConsumer = FlowForImagePerHour()
        )
      ),
      terminationMessage = PoisonPill,
      settings = ClusterSingletonManagerSettings(akkaPaintHistorySystem)
    ),
    name = "PicturePerHourGenerator"
  )

}
class AkkaPaintModule extends AbstractModule {

  def configure() = {

    val akkaPaintHistoryConfig: Config = ConfigFactory.load("akkapaint-history.conf")
    val akkaPaintHistorySystem = ActorSystem("AkkaPaintHistory", akkaPaintHistoryConfig)

    bind(classOf[ActorSystem]).annotatedWith(Names.named("AkkaPaintHistory")).toInstance(akkaPaintHistorySystem)
    bind(classOf[Config]).annotatedWith(Names.named("AkkaPaintHistoryConfig")).toInstance(akkaPaintHistoryConfig)
    bind(classOf[History]).asEagerSingleton()
  }

}
