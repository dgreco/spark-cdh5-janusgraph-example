package it.davidgreco.examples.spark

import java.io.File

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.janusgraph.core.JanusGraphFactory

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Equals",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.AsInstanceOf"))
object SparkMain extends App {

  val yarn = false

  private val initialExecutors = 4

  private val minExecutors = 4

  val sparkConf: SparkConf = new SparkConf().setAppName("spark-cdh5-template-yarn")

  val master: Option[String] = sparkConf.getOption("spark.master")

  val uberJarLocation: String = {
    val location = getJar(SparkMain.getClass)
    if (new File(location).isDirectory) s"${System.getProperty("user.dir")}/assembly/target/scala-2.11/spark-cdh5-janusgraph-example-assembly.jar" else location
  }

  if (master.isEmpty) {
    //it means that we are NOT using spark-submit

    addPath(args(0))

    if (yarn) {
      val _ = sparkConf.
        setMaster("yarn-client").
        setAppName("spark-cdh5-template-yarn").
        setJars(List(uberJarLocation)).
        set("spark.yarn.jars", "local:/opt/cloudera/parcels/SPARK2/lib/spark2/jars/*").
        set("spark.serializer", "org.apache.spark.serializer.KryoSerializer").
        set("spark.io.compression.codec", "lzf").
        set("spark.speculation", "true").
        set("spark.shuffle.manager", "sort").
        set("spark.shuffle.service.enabled", "true").
        set("spark.dynamicAllocation.enabled", "true").
        set("spark.executor.cores", Integer.toString(1)).
        set("spark.executor.memory", "512m").
        set("spark.executor.extraClassPath", "/etc/hbase/conf")
    } else {
      val _ = sparkConf.
        setAppName("spark-cdh5-template-local").
        setMaster("local")
    }
  }

  private val sparkSession: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

  private val rdd = sparkSession.sparkContext.parallelize[Int](1 to 1000, 4)

  rdd foreachPartition {
    iter =>
      lazy val graph = {
        val builder = JanusGraphFactory.build.set("storage.backend", "hbase").set("storage.hostname", "snowwhite.fairytales")
        builder.open()
      }
      try {
        iter.foreach {
          i =>
            val vertex: Vertex = graph.addVertex()
            if (i % 10000 == 0) {
              println(i)
              graph.tx.commit()
            }
        }
      } finally {
        graph.tx().commit()
        graph.close()
      }

  }

  sparkSession.sparkContext.stop()

}