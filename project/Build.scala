import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {

  val appName         = "anormtyped-demo"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    jdbc,
    anorm,
    "postgresql" % "postgresql" % "9.1-901.jdbc4"
  )

  lazy val anormtyped = Project("anormtyped", file("modules/anormtyped")).settings(
    scalaVersion := "2.10.0",
    libraryDependencies <+= (scalaVersion)("org.scala-lang" % "scala-reflect" % _),
    libraryDependencies += component("play"),
    libraryDependencies += anorm
  )

  val demo = play.Project(appName, appVersion, appDependencies).dependsOn(anormtyped)

}
