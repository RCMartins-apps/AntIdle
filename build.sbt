ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

lazy val root =
  (project in file("."))
    .enablePlugins(ScalaJSPlugin)
    .settings(
      name := "AntIdle",
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
