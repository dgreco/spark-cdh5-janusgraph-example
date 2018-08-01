import sbt._

organization := "com.cgnal"

name := "spark-cdh5-janusgraph-example"

version in ThisBuild := "1.0"

/*if it's a library the scope is "compile" since we want the transitive dependencies on the library
  otherwise we set up the scope to "provided" because those dependencies will be assembled in the "assembly"*/
lazy val assemblyDependenciesScope: String = if (isALibrary) "compile" else "provided"

scalaVersion in ThisBuild := "2.11.11"

scalastyleFailOnError := true

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture"
)

wartremoverErrors ++= Warts.all

val sparkExcludes =
  (moduleId: ModuleID) => moduleId.
    exclude("org.apache.hadoop", "hadoop-client").
    exclude("org.apache.hadoop", "hadoop-yarn-client").
    exclude("org.apache.hadoop", "hadoop-yarn-api").
    exclude("org.apache.hadoop", "hadoop-yarn-common").
    exclude("org.apache.hadoop", "hadoop-yarn-server-common").
    exclude("org.apache.hadoop", "hadoop-yarn-server-web-proxy")

val hadoopClientExcludes =
  (moduleId: ModuleID) => moduleId.
    exclude("javax.servlet", "servlet-api").
    exclude("org.slf4j", "slf4j-api").
    exclude("org.slf4j", "slf4j-log4j12")

val hbaseExcludes =
  (moduleID: ModuleID) => moduleID.
    exclude("org.slf4j", "slf4j-log4j12").
    exclude("org.apache.thrift", "thrift").
    exclude("org.jruby", "jruby-complete").
    exclude("org.slf4j", "slf4j-log4j12").
    exclude("org.mortbay.jetty", "jsp-2.1").
    exclude("org.mortbay.jetty", "jsp-api-2.1").
    exclude("org.mortbay.jetty", "servlet-api-2.5").
    exclude("com.sun.jersey", "jersey-core").
    exclude("com.sun.jersey", "jersey-json").
    exclude("com.sun.jersey", "jersey-server").
    exclude("org.mortbay.jetty", "jetty").
    exclude("org.mortbay.jetty", "jetty-util").
    exclude("tomcat", "jasper-runtime").
    exclude("tomcat", "jasper-compiler").
    exclude("commons-logging", "commons-logging").
    exclude("org.apache.xmlgraphics", "batik-ext").
    exclude("commons-collections", "commons-collections").
    exclude("xom", "xom").
    exclude("commons-beanutils", "commons-beanutils")

val hadoopHBaseExcludes =
  (moduleId: ModuleID) => moduleId.
    exclude("org.slf4j", "slf4j-log4j12").
    exclude("javax.servlet", "servlet-api").
    excludeAll(ExclusionRule(organization = "org.mortbay.jetty")).
    excludeAll(ExclusionRule(organization = "javax.servlet"))

//Trick to make Intellij/IDEA happy
//We set all provided dependencies to none, so that they are included in the classpath of root module
lazy val mainRunner = project.in(file("mainRunner")).dependsOn(RootProject(file("."))).settings(
  // we set all provided dependencies to none, so that they are included in the classpath of mainRunner
  libraryDependencies := (libraryDependencies in RootProject(file("."))).value.map {
    module =>
      if (module.configurations.equals(Some("provided"))) {
        module.withConfigurations(Some("compile"))
      } else {
        module
      }
  }
)

lazy val root = (project in file(".")).
  settings(Defaults.itSettings: _*).
  settings(
    libraryDependencies ++= Seq(
     "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-server" % hbaseVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-common" % hbaseVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-testing-util" % hbaseVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-hadoop-compat" % hbaseVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-hadoop-compat" % hbaseVersion % "test" classifier "tests" extra "type" -> "test-jar" ),
      hadoopHBaseExcludes("org.apache.hbase" % "hbase-hadoop2-compat" % hbaseVersion % "test" classifier "tests" extra "type" -> "test-jar"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-client" % hadoopVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-hdfs" % hadoopVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-hdfs" % hadoopVersion % "test" classifier "tests" extra "type" -> "test-jar"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-hdfs" % hadoopVersion % "test" extra "type" -> "test-jar"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-client" % hadoopVersion % "test" classifier "tests"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-minicluster" % hadoopVersion % "test"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-common" % hadoopVersion % "test" classifier "tests" extra "type" -> "test-jar"),
      hadoopHBaseExcludes("org.apache.hadoop" % "hadoop-mapreduce-client-jobclient" % hadoopVersion % "test" classifier "tests")
    )
  ).
  disablePlugins(AssemblyPlugin)

lazy val projectAssembly = (project in file("assembly")).
  settings(
    assemblyShadeRules in assembly := Seq(ShadeRule.rename("com.google.**" -> "shaded.com.google.@1").inAll),
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = true),
    assemblyMergeStrategy in assembly := {
      case "org/apache/spark/unused/UnusedStubClass.class" => MergeStrategy.last
      case "log4j.properties" => MergeStrategy.first
      case "shaded/com/google/common/base/Stopwatch$1.class" => MergeStrategy.last
      case "shaded/com/google/common/base/Stopwatch.class" => MergeStrategy.last
      case "shaded/com/google/common/io/Closeables.class" => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    assemblyJarName in assembly := s"$assemblyName-${version.value}.jar",
    libraryDependencies ++= assemblyDependencies("compile")
  ) dependsOn root settings (
  projectDependencies := {
    Seq(
      (projectID in root).value.excludeAll(ExclusionRule(organization = "org.apache.spark"),
        if (!isALibrary) ExclusionRule(organization = "org.apache.hadoop") else ExclusionRule())
    )
  })

val assemblyName = "spark-cdh5-janusgraph-example-assembly"
val sparkVersion = "2.3.0.cloudera3"
val hadoopVersion = "2.6.0-cdh5.14.0"
val hbaseVersion = "1.2.0-cdh5.14.0"
val scalaTestVersion = "3.0.5"
val guavaVersion = "15.0"

resolvers in ThisBuild ++= Seq(
  Resolver.mavenLocal,
  "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos/"
)

val isALibrary = true //this is a library project

lazy val scope = if (isALibrary) "provided" else "compile"

libraryDependencies in ThisBuild ++= Seq(
  sparkExcludes("org.apache.spark" %% "spark-core" % sparkVersion % scope),
  sparkExcludes("org.apache.spark" %% "spark-sql" % sparkVersion % scope),
  sparkExcludes("org.apache.spark" %% "spark-yarn" % sparkVersion % scope),
  sparkExcludes("org.apache.spark" %% "spark-mllib" % sparkVersion % scope),
  sparkExcludes("org.apache.spark" %% "spark-streaming" % sparkVersion % scope),
  hbaseExcludes("org.apache.hbase" % "hbase-client" % hbaseVersion % scope),
  hbaseExcludes("org.apache.hbase" % "hbase-protocol" % hbaseVersion % scope),
  hbaseExcludes("org.apache.hbase" % "hbase-hadoop-compat" % hbaseVersion % scope),
  hbaseExcludes("org.apache.hbase" % "hbase-server" % hbaseVersion % scope),
  hbaseExcludes("org.apache.hbase" % "hbase-common" % hbaseVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-yarn-api" % hadoopVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-yarn-client" % hadoopVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-yarn-common" % hadoopVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-yarn-applications-distributedshell" % hadoopVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-yarn-server-web-proxy" % hadoopVersion % scope),
  hadoopClientExcludes("org.apache.hadoop" % "hadoop-client" % hadoopVersion % scope)
) ++ assemblyDependencies(assemblyDependenciesScope)

dependencyOverrides in ThisBuild += "com.google.guava" % "guava" % guavaVersion

//http://stackoverflow.com/questions/18838944/how-to-add-provided-dependencies-back-to-run-test-tasks-classpath/21803413#21803413
run in Compile := Defaults.runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run))

//http://stackoverflow.com/questions/27824281/sparksql-missingrequirementerror-when-registering-table
fork := true

parallelExecution in Test := false

val assemblyDependencies = (_: String) => Seq(
  "org.janusgraph" % "janusgraph-hbase" % "0.3.0"
    exclude("org.apache.tinkerpop", "gremlin-groovy")
    exclude("org.codehaus.groovy", "*")
    exclude("org.slf4j", "slf4j-log4j12")
    exclude("org.slf4j", "jcl-over-slf4j")
    exclude("commons-logging", "commons-logging"),
  "org.janusgraph" % "janusgraph-hadoop-core" % "0.3.0"
    exclude("org.apache.spark", "*")
    exclude("org.apache.hadoop", "*")
    exclude("org.apache.tinkerpop", "gremlin-groovy")
    exclude("org.janusgraph", "janusgraph-hbase-core")
    exclude("org.janusgraph", "janusgraph-es")
    exclude("org.janusgraph", "janusgraph-cassandra")
    exclude("com.datastax.cassandra", "cassandra-driver-core")
    exclude("commons-logging", "commons-logging"),
  hbaseExcludes("org.apache.hbase" % "hbase-client" % hbaseVersion),
  hbaseExcludes("org.apache.hbase" % "hbase-server" % hbaseVersion),
  hbaseExcludes("org.apache.hbase" % "hbase-common" % hbaseVersion),
  hbaseExcludes("org.apache.hbase" % "hbase-hadoop-compat" % hbaseVersion)
)