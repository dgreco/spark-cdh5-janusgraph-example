package it.davidgreco.examples.spark.janusgraph

import java.time.Instant
import java.util.Date

import org.apache.hadoop.hbase.HBaseTestingUtility
import org.apache.hadoop.hbase.client.Connection
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.tinkerpop.gremlin.process.traversal.P
import org.apache.tinkerpop.gremlin.structure.T.label
import org.apache.tinkerpop.gremlin.structure.{Edge, Vertex}
import org.janusgraph.core.schema.JanusGraphManagement
import org.janusgraph.core.{Cardinality, JanusGraphFactory, Multiplicity}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}

import scala.collection.immutable.Stream.Empty

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Recursion",
    "org.wartremover.warts.Nothing",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Overloading"))
class SparkSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  private var sparkSession: SparkSession = _

  private val hbaseUtil = new HBaseTestingUtility()

  override def beforeAll(): Unit = {
    val _ = hbaseUtil.startMiniCluster(1)
    SparkSpec.connection = Some(hbaseUtil.getConnection)
    val conf = new SparkConf().
      setAppName("spark-cdh5-template-local-test").
      setMaster("local[4]")
    sparkSession = SparkSession.builder().config(conf).getOrCreate()
    ()
  }

  override def afterAll(): Unit = {
    sparkSession.stop()
    hbaseUtil.shutdownMiniCluster()
  }

  "A simple test" must {
    "pass" in {

      val builder = JanusGraphFactory.build.
        set("storage.backend", "hbase").
        set("storage.hostname", "localhost").
        set("storage.hbase.ext.hbase.zookeeper.property.clientPort", s"${hbaseUtil.getConfiguration.get("hbase.zookeeper.property.clientPort")}")

      val graph = builder.open()

      val mgmt: JanusGraphManagement = graph.openManagement()

      val calledEdgeLabel = mgmt.makeEdgeLabel("CALLED").directed().multiplicity(Multiplicity.MULTI).make()

      val whenCalledEdgeProperty = mgmt.makePropertyKey("CALLEDWHEN").dataType(classOf[java.util.Date]).cardinality(Cardinality.SINGLE).make()

      val pstnVertexLabel = mgmt.makeVertexLabel("PSTN")

      val pstnVertexProperty = mgmt.makePropertyKey("PSTN").dataType(classOf[String]).cardinality(Cardinality.SINGLE).make()

      val index1 = mgmt.buildIndex("pstnIndex", classOf[Vertex]).addKey(pstnVertexProperty).unique().buildCompositeIndex()

      val index2 = mgmt.buildIndex("whenCalledIndex", classOf[Edge]).addKey(whenCalledEdgeProperty).buildCompositeIndex

      mgmt.commit()

      val v1 = graph.addVertex(label, "PSTN")

      v1.property("PSTN", "063397771")

      val v2 = graph.addVertex(label, "PSTN")

      v2.property("PSTN", "06345673")

      val e1 = v1.addEdge("CALLED", v2)

      e1.property("CALLEDWHEN", Date.from(Instant.now()))

      graph.tx().commit()

      val g = graph.traversal()

      val vertexes = g.V()

      def getVertexStream: Stream[Vertex] =
        if (vertexes.hasNext)
          vertexes.next() #:: getVertexStream
        else
          Empty

      getVertexStream.foreach(v => {
        import collection.convert.decorateAsScala._
        println(v.properties().asScala.toList)
      })

      val v1l = g.V().has("PSTN", P.eq("063387273")).next()

      graph.close()
    }
  }

}

object SparkSpec {
  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  var connection: Option[Connection] = None
}
