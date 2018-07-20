package it.davidgreco.examples.spark.janusgraph

import java.io.File

import org.apache.commons.configuration.{ BaseConfiguration, Configuration }
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapreduce.InputFormat
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.tinkerpop.gremlin.hadoop.Constants
import org.apache.tinkerpop.gremlin.hadoop.structure.io.VertexWritable
import org.apache.tinkerpop.gremlin.hadoop.structure.util.ConfUtil

@SuppressWarnings(
  Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Equals"))
object SparkScanMain extends App {

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
        set("spark.executor.cores", Integer.toString(1)).
        set("spark.executor.memory", "512m").
        set("spark.executor.extraClassPath", "/etc/hbase/conf")
    } else {
      val _ = sparkConf.
        setAppName("spark-cdh5-janusgraph-example-local").
        setMaster("local")
    }
  }

  private val conf: Configuration = new BaseConfiguration()

  conf.setProperty("gremlin.graph", "org.apache.tinkerpop.gremlin.hadoop.structure.HadoopGraph")

  conf.setProperty("gremlin.hadoop.graphReader", "org.janusgraph.hadoop.formats.hbase.HBaseInputFormat")

  conf.setProperty("gremlin.hadoop.graphWriter", "org.apache.tinkerpop.gremlin.hadoop.structure.io.gryo.GryoOutputFormat")

  conf.setProperty("janusgraphmr.ioformat.conf.storage.backend", "hbase")

  conf.setProperty("janusgraphmr.ioformat.conf.storage.hostname", "snowwhite.fairytales")

  conf.setProperty("janusgraphmr.ioformat.conf.storage.hbase.table", "janusgraph")

  conf.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

  private val sparkSession: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

  private val hadoopConfiguration = ConfUtil.makeHadoopConfiguration(conf)

  private val rdd: RDD[(NullWritable, VertexWritable)] =
    sparkSession.sparkContext.
      newAPIHadoopRDD(
        hadoopConfiguration,
        hadoopConfiguration.getClass(Constants.GREMLIN_HADOOP_GRAPH_READER, classOf[InputFormat[NullWritable, VertexWritable]]).
          asInstanceOf[Class[InputFormat[NullWritable, VertexWritable]]],
        classOf[NullWritable], classOf[VertexWritable])

  rdd.map(_._2).collect().foreach(println(_))

  sparkSession.sparkContext.stop()

}
