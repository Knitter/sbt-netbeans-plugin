package netbeans

import sbt._
import Keys._
import project._
import CommandSupport._

object NetbeansPlugin extends Plugin {

  val sbtExecutable = SettingKey[String]("sbt-executable")

  /** build.xml template under ./sbt/plugins (overrides the template in plugin jar) */
  private val buildXmlTemplateLocation = (state: State) => BuildPaths.getGlobalPluginsDirectory(state, BuildPaths.defaultGlobalBase) / "src" / "main" / "resources" / "build.xml"
  /** project.properties template under ./sbt/plugins (overrides the template in plugin jar) */
  private val projectFilesTemplateLocation = (state: State) => BuildPaths.getGlobalPluginsDirectory(state, BuildPaths.defaultGlobalBase) / "src" / "main" / "resources" / "nbproject"

  /** Extracts Netbeans project files templates from the plugin jar */
  private def copyNetbeansFiles(basePath: File)(pluginJarPath: File) =
    IO.unzip(pluginJarPath, basePath, "*.xml" | "*.properties")

  /** Copies packed Netbeans project files templates to the project folder */
  private def copyPackedTemplates(libClasspath: Seq[File], dest: File) =
    libClasspath.filter(_.getName.contains("sbt-netbeans-plugin"))
  .headOption.map(copyNetbeansFiles(dest))

  /** Copies Netbeans project files templates from ./sbt/plugins */
  private def copyUnpackedTemplates(dest: File, state: State) = {
    buildXmlTemplateLocation(state).get.map { template =>
      IO.copy(Seq(template.asFile -> (dest / "build.xml").asFile), false)
    }
    projectFilesTemplateLocation(state).get.map { template =>
      IO.copyDirectory(template.asFile, (dest / "nbproject").asFile, false)
    }
  }

  /** Adds sbt-netbeans commands globally */
  override lazy val settings = Seq(commands += netbeansCommands)

  /** Updates Netbeans project files with SBT project settings */
  private[netbeans] def updateNetbeansFiles(projectRef: ProjectRef,
                                            s: State,
                                            projectFiles: Seq[ProjectContext => NetbeansConfigFile]): State = {

    import scalaz.Scalaz._

    s.log.info("Updating Netbeans files for project `%s`..." format(projectRef.project))

    ProjectContext.netbeansContext(projectRef, s).map{context =>
      projectFiles.map(_(context)).foreach{ projectFile =>
        projectFile.validate.fold ({ errors =>
            s.log.error("%s: failed to update %s"
                            .format(projectRef.project, projectFile.description))
          }, { _ =>
            projectFile.store()
            s.log.info("%s: successfully updated %s"
                           .format(projectRef.project, projectFile.description))
          }
        )
      }
    }

    s
  }

  /** Updates all Netbeans project files */
  private[netbeans] def updateAll(projectRef: ProjectRef)(s: State): State =
    updateNetbeansFiles(projectRef, s,
                        Seq((context => AntScript(context.baseDirectory / "build.xml")(context)),
                            (context => ProjectConfiguration(context.baseDirectory / "nbproject" /"project.xml")(context)),
                            (context => ProjectProperties(context.baseDirectory / "nbproject" / "project.properties")(context)))
    )

  /** Updates project.properties */
  private[netbeans] def updateProjectProperties(projectRef: ProjectRef)(s: State): State =
    updateNetbeansFiles(projectRef, s, Seq((context => ProjectProperties(context.baseDirectory / "nbproject" /"project.properties")(context))))

  /** Updates project.xml */
  private[netbeans] def updateProjectConfig(projectRef: ProjectRef)(s: State): State =
    updateNetbeansFiles(projectRef, s, Seq((context => ProjectConfiguration(context.baseDirectory / "nbproject" /"project.xml")(context))))

  /** Creates empty source/resource directories */
  private[netbeans] def createSourceDirectories(projectRef: ProjectRef)(s: State): State = {
    ProjectContext.netbeansContext(projectRef, s).map{context =>
      s.log.info("Creating empty source directories for project `%s`..." format(projectRef.project))

      IO.createDirectories(Seq(
          context.baseDirectory / "src" / "main" / "scala",
          context.baseDirectory / "src" / "test" / "scala",
          context.baseDirectory / "src" / "main" / "resources",
          context.baseDirectory / "src" / "test" / "resources"
        ))
    }
    s
  }

  /** Adds Netbeans project files to the SBT project */
  private[netbeans] def createNetbeansFiles(projectRef: ProjectRef)(s: State): State = {
    val extracted = Project extract s
    import extracted._

    s.log.info("Creating Netbeans files for project `%s`" format(projectRef.project))
    for{base <- baseDirectory in (projectRef, Compile) get structure.data}{
      copyPackedTemplates(currentUnit.unit.plugins.classpath,
                          base)
      copyUnpackedTemplates(base, s)
    }

    if(session.original.filter(_.key.key.label == NetbeansTasks.updateDepTaskKey).isEmpty){
      s.log.info("Writing plugin settings for project `%s`" format(projectRef.project))
      NetbeansTasks.writePluginSettings(projectRef, structure)
      s.reload
    } else s
  }

  /** Removes Netbeans project files from the SBT project */
  private[netbeans] def removeNetbeansFiles(projectRef: ProjectRef)(s: State): State = {
    val extracted = Project extract s
    import extracted._

    s.log.info("Removing Netbeans files from project `%s`" format(projectRef.project))
    (baseDirectory in (projectRef, Compile) get structure.data) map { base =>
      IO.delete((base / "build.xml" +++ base / "nbproject").get)
    }

    s
  }

  type NetbeansCommand = ProjectRef => State => State

  lazy val netbeansCommands =
    Command("netbeans")(_ => NetbeansCommands.netbeansConsole) { (state: State, output: Any) =>
      output match {
        case (cmd: NetbeansCommand, transitive: Boolean) =>
          val extracted = Project extract state
          val s = cmd(extracted.currentRef)(state)
          if(transitive){
            state.log.info("Executing command transitively...")
            (s /: extracted.currentProject.uses) {(_s, _ref) => cmd(_ref)(_s)}
          } else s
        case other =>
          state.log.error("Failed to process command line: " + other)
          state.fail
      }
    }

}
