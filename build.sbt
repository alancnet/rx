name := "rx"

organization := "net.alanc"

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
  <url>https://github.com/alancnet/rx</url>
  <licenses>
    <license>
      <name>BSD-style</name>
      <url>http://www.opensource.org/licenses/bsd-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:alancnet/rx.git</url>
    <connection>scm:git:git@github.com:alancnet/rx.git</connection>
  </scm>
  <developers>
    <developer>
      <id>alancnet</id>
      <name>Alan Colon</name>
      <url>http://www.alanc.net</url>
    </developer>
  </developers>
