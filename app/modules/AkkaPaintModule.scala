package modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import org.akkapaint.history.AkkaPaintHistoryGenerator

class HistoryGenerator {
  new AkkaPaintHistoryGenerator().run()
}
class AkkaPaintModule extends AbstractModule {
  def configure() = {

    bind(classOf[HistoryGenerator]).asEagerSingleton
  }
}