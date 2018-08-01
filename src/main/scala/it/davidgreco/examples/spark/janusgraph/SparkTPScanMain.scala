package it.davidgreco.examples.spark.janusgraph

import java.io.File

import org.apache.commons.configuration.{ BaseConfiguration, Configuration }
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.tinkerpop.gremlin.spark.process.computer.SparkGraphComputer
import org.apache.tinkerpop.gremlin.spark.structure.Spark
import org.apache.tinkerpop.gremlin.structure.util.GraphFactory

@SuppressWarnings(
  Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Equals"))
object SparkTPScanMain extends App {

  val yarn = true

  val sparkConf: SparkConf = new SparkConf()

  val master: Option[String] = sparkConf.getOption("spark.master")

  val uberJarLocation: String = {
    val location = getJar(SparkMain.getClass)
    if (new File(location).isDirectory)
      s"${System.getProperty("user.dir")}/assembly/target/scala-2.11/spark-cdh5-janusgraph-example-assembly-1.0.jar"
    else
      location
  }

  if (master.isEmpty) {
    //it means that we are NOT using spark-submit

    addPath(args(0))

    if (yarn) {
      val _ = sparkConf.
        setMaster("yarn").
        setAppName("spark-cdh5-janusgraph-example-yarn").
        setJars(List(uberJarLocation)).
        set("spark.yarn.jars", "local:/opt/cloudera/parcels/SPARK2/lib/spark2/jars/*").
        set("spark.serializer", "org.apache.spark.serializer.KryoSerializer").
        set("spark.io.compression.codec", "lzf").
        set("spark.speculation", "true").
        set("spark.shuffle.manager", "sort").
        set("spark.shuffle.service.enabled", "true").
        set("spark.dynamicAllocation.enabled", "true").
        set("spark.executor.cores", Integer.toString(2)).
        set("spark.executor.memory", "2048m").
        set("spark.executor.extraClassPath", "/etc/hbase/conf")
    } else {
      val _ = sparkConf.
        setAppName("spark-cdh5-janusgraph-example-local").
        setMaster("local")
    }
  }

  private val sparkSession: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

  private val gremlinSpark = Spark.create(sparkSession.sparkContext)

  private val sparkComputerConnection = GraphFactory.open(getConf())

  private val traversalSource = sparkComputerConnection.traversal().withComputer(classOf[SparkGraphComputer])

  println(traversalSource.E().count().next())

  traversalSource.close()

  sparkComputerConnection.close()

  sparkSession.stop()

  private def getConf() = {
    val conf: Configuration = new BaseConfiguration()

    conf.setProperty("gremlin.graph", "org.apache.tinkerpop.gremlin.hadoop.structure.HadoopGraph")

    conf.setProperty("gremlin.hadoop.graphReader", "org.janusgraph.hadoop.formats.hbase.HBaseInputFormat")

    conf.setProperty("gremlin.hadoop.graphWriter", "org.apache.tinkerpop.gremlin.hadoop.structure.io.gryo.GryoOutputFormat")

    conf.setProperty("storage.backend", "hbase")

    conf.setProperty("storage.hostname", "snowwhite.fairytales")

    conf.setProperty("janusgraphmr.ioformat.conf.storage.backend", "hbase")

    conf.setProperty("janusgraphmr.ioformat.conf.storage.hostname", "snowwhite.fairytales")

    conf.setProperty("janusgraphmr.ioformat.conf.storage.hbase.table", "janusgraph")

    conf.setProperty("spark.master", "local")

    conf.setProperty("spark.executor.memory", "1g")

    conf.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

    conf
  }

}
