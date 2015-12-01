name := "rxio"

version := "0.0.1"

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "io.reactivex" %% "rxscala" % "0.25.0",
  "io.reactivex" % "rxjava-string" % "0.22.0",
  "org.scalatest" %% "scalatest" % "2.2.2" % "test",
  "org.scala-lang.modules" %% "scala-async" % "0.9.5",
  "com.typesafe.akka" %% "akka-actor" % "2.3.14",
  "commons-codec" % "commons-codec" % "1.10",
  "com.jsuereth" %% "scala-arm" % "1.4"
)

pomExtra :=
  <url>http://jsuereth.com/scala-arm</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:jsuereth/scala-arm.git</url>
    <connection>scm:git:git@github.com:jsuereth/scala-arm.git</connection>
  </scm>
  <developers>
    <developer>
      <id>jsuereth</id>
      <name>Josh Suereth</name>
      <url>http://jsuereth.com</url>
    </developer>
  </developers>
