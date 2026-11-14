import scala.sys.process._
import scala.util.Try

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root =
  (project in file("."))
    .enablePlugins(ScalaJSPlugin)
    .settings(
      name := "AntIdle",
      Compile / sourceGenerators += Def.task {
        val directory = baseDirectory.value
        val quiet = ProcessLogger(_ => (), _ => ())
        val revision = Try(Process(Seq("git", "rev-parse", "--verify", "HEAD"), directory).!!(quiet).trim)
          .toOption.filter(_.matches("[0-9a-f]{40,64}"))
        val modified = revision.isDefined &&
          Try(Process(Seq("git", "diff", "--quiet", "HEAD", "--"), directory).!(quiet)).getOrElse(1) != 0
        val commit = revision.getOrElse("development")
        val output = (Compile / sourceManaged).value / "pt" / "rcmartins" / "antidle" / "game" / "BuildInfo.scala"
        val source = s"""package pt.rcmartins.antidle.game
                       |// Generated at compilation; identifies the running bundle, never the save schema.
                       |object BuildInfo {
                       |  val commit: String = "$commit"
                       |  val modified: Boolean = $modified
                       |  val label: String = "${revision.map(_.take(8)).getOrElse("development")}${if (modified) "-dev" else ""}"
                       |}
                       |""".stripMargin
        if (!output.exists || IO.read(output) != source) IO.write(output, source)
        Seq(output)
      }.taskValue,

      scalacOptions ++= ScalacOptions.allScalacOptions,
      scalaJSUseMainModuleInitializer := true,
      libraryDependencies ++= Seq(
        "com.raquo"                  %%% "laminar"       % "17.1.0",
        "org.querki"                 %%% "jquery-facade" % "2.1",
        "dev.zio"                    %%% "zio"           % "2.1.9",
        "dev.zio"                    %%% "zio-json"      % "0.7.3",
        "dev.zio"                    %%% "zio-prelude"   % "1.0.0-RC31",
        "org.scalameta"              %%% "munit"         % "1.0.0" % Test,
        "com.softwaremill.quicklens" %%% "quicklens"     % "1.9.8",
        // java.time library support for Scala.js
        "io.github.cquiroz" %%% "scala-java-time"      % "2.6.0",
        "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.6.0",
      )
    )

Global / onChangedBuildSource := ReloadOnSourceChanges

addCommandAlias("c", "fastOptJS")

// Developer-only executable: reuses game IR without starting the browser main.
lazy val balance = (project in file("tools/balance"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(root)
  .settings(
    name := "AntIdleBalance",
    scalaJSUseMainModuleInitializer := true,
    Compile / mainClass := Some("pt.rcmartins.antidle.balance.BalanceMain"),
    scalacOptions ++= ScalacOptions.allScalacOptions,
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
  )
