**sbt-netbeans-plugin** is a plugin for simple-build-tool that allows working with SBT projects in Netbeans IDE.

### Installing the plugin

You can either add sources of the plugin to `~/.sbt/plugins` or add a managed dependency to the plugin artifact (in the both cases, plugin will be globally available):

    $ cd .sbt/plugins/
    $ xsbt
    > set resolvers += "remeniuk repo" at "http://remeniuk.github.com/maven" 
    > set libraryDependencies += "org.netbeans" %% "sbt-netbeans-plugin" % "0.0.6_0.9.5"
    > session save
    > exit

### Building from source

Clone **sbt-netbeans-plugin**:

    $ git clone -n git://github.com/remeniuk/sbt-netbeans-plugin.git
    $ cd sbt-netbeans-plugin

Switch to 0.9 branch:

    $ git checkout 0.9

Publish to the local ivy repository:

    $ xsbt publish-local

### Using the plugin

By default, any command is applied only to the current project. If you want to apply it to all the projects it depends on or aggregates, `transitive` command should be added (e.g., `netbeans create transitive` applied to the root project will create Netbeans files not only for the root, but also for all the dependencies, etc.).

Create Netbeans files:

    > netbeans create

,or simply:

    > netbeans

*Along with the Netbeans project files, `netbeans-update-dependencies` task is added to the project settings. This task is automatically triggered every time `update` is called, and checks, if Netbeans project classpath matches SBT classpath. If classpaths differ, `project.properties` is updated. Therefore, when you add a dependency to Netbeans (either managed or unmanaged), and call `update`, those dependencies are immediately plugged to the project and reflected in the IDE*

**Now, you can open SBT project in Netbeans!**

Create an empty source/resource folders:

    > netbeans create source-directories

Update Netbeans files with SBT project settings:

    > netbeans update all

,or:

    > netbeans update

If you don't want to add `netbeans-update-dependencies` to the project settings, you will have to trigger update of the Netbeans project properties manually. In order to update only those files that contain project classpaths, use the following command:

    > netbeans update dependencies

Remove Netbeans files:

    > netbeans remove
