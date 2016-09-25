package org.akkapaint.shard

import com.typesafe.config.ConfigFactory
import org.akkapaint.shard.cluster.AkkaPaintShardingCluster

object AkkaPaintCluster extends App {
  new AkkaPaintShardingCluster().initializeClusterSharding(ConfigFactory.load("akkapaint-cluster.conf"))
}
