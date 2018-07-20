package it.davidgreco.examples.spark

import java.io.File
import java.net.{ URL, URLClassLoader }

package object janusgraph {
  //Simple function for adding a directory to the system classpath
  def addPath(dir: String): Unit = {
    val method = classOf[URLClassLoader].getDeclaredMethod("addURL", classOf[URL])
    method.setAccessible(true)
    val dummy = method.invoke(ClassLoader.getSystemClassLoader, new File(dir).toURI.toURL)
  }

  //given a class it returns the jar (in the classpath) containing that class
  def getJar(klass: Class[_]): String = {
    val codeSource = klass.getProtectionDomain.getCodeSource
    codeSource.getLocation.getPath
  }

}
