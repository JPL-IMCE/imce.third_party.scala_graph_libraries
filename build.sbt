import sbt.Keys._
import sbt._
import net.virtualvoid.sbt.graph._

import gov.nasa.jpl.imce.sbt._

updateOptions := updateOptions.value.withCachedResolution(true)

val resourceArtifact = settingKey[Artifact]("Specifies the project's resource artifact")

def IMCEThirdPartyProject(projectName: String, location: String): Project =
  Project(projectName, file("."))
    .enablePlugins(IMCEGitPlugin)
    .enablePlugins(IMCEReleasePlugin)
    .settings(IMCEReleasePlugin.packageReleaseProcessSettings)
    .settings(
      IMCEKeys.targetJDK := IMCEKeys.jdk18.value,
      IMCEKeys.licenseYearOrRange := "2015-2016",
      IMCEKeys.organizationInfo := IMCEPlugin.Organizations.thirdParty,
      git.baseVersion := Versions.version,
      scalaVersion := Versions.scala_version,
      projectID := {
        val previous = projectID.value
        previous.extra(
          "build.date.utc" -> buildUTCDate.value,
          "artifact.kind" -> "third_party.aggregate.libraries")
      }
    )
    .settings(

      // disable publishing the main jar produced by `package`
      publishArtifact in(Compile, packageBin) := false,

      // disable publishing the main API jar
      publishArtifact in(Compile, packageDoc) := false,

      // disable publishing the main sources jar
      publishArtifact in(Compile, packageSrc) := false,

      // disable publishing the jar produced by `test:package`
      publishArtifact in(Test, packageBin) := false,

      // disable publishing the test API jar
      publishArtifact in(Test, packageDoc) := false,

      // disable publishing the test sources jar
      publishArtifact in(Test, packageSrc) := false,

      // name the '*-resource.zip' in the same way as other artifacts
      com.typesafe.sbt.packager.Keys.packageName in Universal :=
        normalizedName.value + "_" + scalaBinaryVersion.value + "-" + version.value,

      resourceArtifact := Artifact((name in Universal).value, "zip", "zip", Some("resource"), Seq(), None, Map()),

      artifacts += resourceArtifact.value,

      // contents of the '*-resource.zip' to be produced by 'universal:packageBin'
      mappings in Universal ++= {
        val appC = appConfiguration.value
        val cpT = classpathTypes.value
        val up = update.value
        val s = streams.value

        def getFileIfExists(f: File, where: String)
        : Option[(File, String)] =
          if (f.exists()) Some((f, s"$where/${f.getName}")) else None

        val ivyHome: File =
          Classpaths
            .bootIvyHome(appC)
            .getOrElse(sys.error("Launcher did not provide the Ivy home directory."))

        val libDir = location + "/lib/"
        val srcDir = location + "/lib.sources/"
        val docDir = location + "/lib.javadoc/"

        s.log.info(s"====== $projectName =====")

        val compileConfig: ConfigurationReport = {
          up.configurations.find((c: ConfigurationReport) => Configurations.Compile.name == c.configuration).get
        }

        def transitiveScope(modules: Set[Module], g: ModuleGraph): Set[Module] = {

          @annotation.tailrec
          def acc(focus: Set[Module], result: Set[Module]): Set[Module] = {
            val next = g.edges.flatMap { case (fID, tID) =>
              focus.find(m => m.id == fID).flatMap { _ =>
                g.nodes.find(m => m.id == tID)
              }
            }.to[Set]
            if (next.isEmpty)
              result
            else
              acc(next, result ++ next)
          }

          acc(modules, Set())
        }

        val zipFiles: Set[File] = {
          val jars = for {
            oReport <- compileConfig.details
            mReport <- oReport.modules
            (artifact, file) <- mReport.artifacts
            if "zip" == artifact.extension
            file <- {
              s.log.info(s"compile: ${oReport.organization}, ${file.name}")
              val graph = backend.SbtUpdateReport.fromConfigurationReport(compileConfig, mReport.module)
              val roots: Set[Module] = graph.nodes.filter { m =>
                m.id.organisation == mReport.module.organization &&
                  m.id.name == mReport.module.name &&
                  m.id.version == mReport.module.revision
              }.to[Set]
              val scope: Seq[Module] = transitiveScope(roots, graph).to[Seq].sortBy(m => m.id.organisation + m.id.name)

              val files = scope.flatMap { m: Module => m.jarFile }.to[Seq].sorted
              s.log.info(s"Excluding ${files.size} jars from zip aggregate resource dependencies")
              require(
                files.nonEmpty,
                s"There should be some excluded dependencies\ngraph=$graph\nroots=$roots\nscope=$scope")
              files.foreach { f =>
                s.log.info(s" exclude: ${f.getParentFile.getParentFile.name}/${f.getParentFile.name}/${f.name}")
              }
              files
            }
          } yield file
          jars.to[Set]
        }

        val fileArtifacts = for {
          oReport <- compileConfig.details
          organizationArtifactKey = s"{oReport.organization},${oReport.name}"
          mReport <- oReport.modules
          (artifact, file) <- mReport.artifacts
          if !mReport.evicted && "jar" == artifact.extension && !zipFiles.contains(file)
        } yield (oReport.organization, oReport.name, file, artifact)

        val fileArtifactsByType = fileArtifacts.groupBy { case (_, _, _, a) =>
          a.`classifier`.getOrElse(a.`type`)
        }
        val jarArtifacts = fileArtifactsByType("jar").map { case (o, _, jar, _) => o -> jar }.to[Set].to[Seq].sortBy { case (o, jar) => s"$o/${jar.name}" }
        val srcArtifacts = fileArtifactsByType("sources").map { case (o, _, jar, _) => o -> jar }.to[Set].to[Seq].sortBy { case (o, jar) => s"$o/${jar.name}" }
        val docArtifacts = fileArtifactsByType("javadoc").map { case (o, _, jar, _) => o -> jar }.to[Set].to[Seq].sortBy { case (o, jar) => s"$o/${jar.name}" }

        val jars = jarArtifacts.map { case (o, jar) =>
          s.log.info(s"* jar: $o/${jar.name}")
          jar -> (libDir + jar.name)
        }
        val srcs = srcArtifacts.map { case (o, jar) =>
          s.log.info(s"* src: $o/${jar.name}")
          jar -> (srcDir + jar.name)
        }
        val docs = docArtifacts.map { case (o, jar) =>
          s.log.info(s"* doc: $o/${jar.name}")
          jar -> (docDir + jar.name)
        }

        jars ++ srcs ++ docs
      },

      extractArchives := {},

      artifacts += {
        val n = (name in Universal).value
        Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map())
      },

      packagedArtifacts += {
        val p = (packageBin in Universal).value
        val n = (name in Universal).value
        Artifact(n, "zip", "zip", Some("resource"), Seq(), None, Map()) -> p
      }
    )

lazy val graphLibs = IMCEThirdPartyProject("scala-graph-libraries", "graphLibs")
  .settings(
    resolvers += Resolver.bintrayRepo("jpl-imce", "gov.nasa.jpl.imce"),

    libraryDependencies ++= Seq(
      "gov.nasa.jpl.imce" %% "imce.third_party.other_scala_libraries" % Versions_other_scala_libraries.version
        % "compile"
        artifacts
        Artifact("imce.third_party.other_scala_libraries", "zip", "zip", Some("resource"), Seq(), None, Map()),

      "org.scala-lang" % "scalap" % scalaVersion.value % "provided",

      "com.assembla.scala-incubator" %% "graph-core" % Versions.graph_core %
      "compile" withSources() withJavadoc(),

      "com.assembla.scala-incubator" %% "graph-constrained" % Versions.graph_constrained %
      "compile" withSources() withJavadoc(),

      "com.assembla.scala-incubator" %% "graph-json" % Versions.graph_json %
      "compile" withSources() withJavadoc(),

      "com.assembla.scala-incubator" %% "graph-dot" % Versions.graph_dot %
      "compile" withSources() withJavadoc()
    ))
