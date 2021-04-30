# jtreg plugin for IntelliJ IDE
##### *Maurizio Cimadamore and Chris Hegarty, September 2016, version 0.3*

This is a convenience plugin which adds jtreg capabilities to the IntelliJ IDE. With this plugin, OpenJDK developers
can write, run, debug jtreg tests without the need of leaving their IDE environment.

### Changes from 0.2

* updated instruction on how to build the plugin

### Changes from 0.1

* more detailed instructions on how to configure the plugin project
* updated section `What needs to be rebuilt before a test run?` to reflect latest plugin changes

## Plugin setup

This section covers the initial steps that are required in order to correctly setup the plugin project inside the IntelliJ IDE.

### Folder layout

The output of this folder is as follows:

```
 idea
   |-src (plugin sources)
   |-build (where build files are stored)
   |-resources (plugin resources - the plugin.xml file lives here)
   |-.idea (a template project for editing/building/testing the plugin itself)
```

### Building the plugin

To build the plugin, you need to have IntelliJ installed. From IntelliJ, simply browse to the location of this folder, and open it as an IntelliJ project. The project can be built by clicking on `Build -> Build Project`. Once this step is done, you should be able to run/debug the plugin.

Note: You might need to tweak the IDE project settings to correctly configure the plugin project. The two following steps are required:

* When the IDE project for the plugin is first opened, IntelliJ will complain about the lack of a path variable called `JTREG_HOME`. Please follow the IDE instructions, and set this path variable to point to the root of your jtreg build output or your jtreg installation (`JTREG_HOME/libs/javatest.jar` must exist). The setting can also be found in under `Settings -> Appearance and Behavior -> Path Variables`. This step is required as the project depends on some of the jtreg libraries.

* You need to create an Intellij SDK and name it `IDEA JDK` for the project to work correctly. To create a new SDK, click on `File -> Project Structure`. When the dialog opens, click on the `SDKs` entry in the left pane, and add a new SDK (by clicking on the *plus* icon). The SDK we want to create is an 'IntelliJ Platform Plugin SDK'. Once the new SDK type has been selected, you will have to tell IntelliJ what's the SDK path - you will see that IntelliJ will already point to the folder containing your IntelliJ installation - that is a fine choice. Rename it to `IDEA JDK`. Secondly, you will have to tell IntelliJ which JDK to use together with the plugin SDK. Any JDK 8 (or greater) is a fine choice here.
  
Note: by running the plugin here we mean running it in a sandbox environment - for truly installing the plugin in your IDE, see the steps below.

### Installing the plugin

To install the plugin in your IDE, first you need to prepare a plugin module file (a `.jar` file). This can be done by selecting `Build -> Prepare Plugin Module for Deployment`. Once that is done, a new jar file should be created under the hidden `.idea` folder (and a related pop up notification should appear).

Once you have a jar file, you can easily install it in your IDE; go in `File -> Settings`, and select `Plugins` from the right panel. Then click on `Install plugin from disk` and point the IDE to the zip file you have created in the step above. The IDE will ask you to restart - once restart is completed the installation process is completed, and the plugin is ready to be used to run and debug jtreg tests.

## Using the plugin

This section covers the the functionalities supported by the jtreg plugin.

### Configuring jtreg

The plugin allows user to configure how jtreg should be invoked by the plugin. Possible options include:

* which jtreg should be used to run the tests
* which JDK should be used to run the tests
* which jtreg options should be used to run tests
* where jtreg should put its output files

Configuring such parameters is possible through the dialog located under `File -> Settings`, under the `jtreg` panel. By default, such settings are saved on a per-project basis. If, however, you would like to specify IDE-wide default, you can do so by accessing a similar dialog under `File -> Other Settings -> Default Settings`.

Note: the settings specified here will be used as defaults for creating new jtreg run/debug configurations. As a result, if any error is made when configuring global jtreg settings, such errors will be propagated in all newly created jtreg test configurations.

### Running/debugging jtreg tests

In order to run a test, you need to create a run configuration. To create a configuration, simply open a jtreg test file, right-click on it and select `Run` (or `Debug`). This should create a configuration for the selected test, and run that configuration. The configuration will then become available for inspection under the `Run -> Edit Configurations` menu. If you wish to run all tests in a given folder, the process is the same: simply right click on the test folder (in the left panel) of choice and select `Run`.

A jtreg run configuration contains the following information:

* which test (or folder of tests) should be given as input to jtreg
* which JDK should be used to run the tests
* which jtreg options should be used to run tests
* where jtreg should put its output files

The last three options can be used to override global settings (e.g. in case a test needs more memory to run).

Once a configuration has been created, the new configuration will appear in the top right panel. If you notice a red mark beside the configuration name, it means that the configuration contains errors that will likely result in test failures. If that happens, please open the `Run -> Edit Configurations` dialog, and select the problematic configuration; this dialog should contain additional information as to why the configuration is problematic (e.g. missing JDK path).

To run an existing configuration, simply select it from the drop down list in the top right panel, and click on the `Run` (an icon similar to a *play* button) icon. If you wish to debug instead, simply press the icon beside `Run` (the one with the little bug in it).

Debugging works as for any other Java application. Simply set breakpoints (this can be done by left-clicking the area to the left of the code in the source editor, which will cause a red circle to appear). During a test debug, execution will stop at any given breakpoints, allowing you to see values of variables, set watch expressions, etc.

### Inspecting jtreg test results

Once a test (or a group of tests) have finished running, you can inspect test results in the corresponding bottom panel. This panel is organized in two sub-panels: the one on the left allows you to chose the test whose results you'd like to inspect; the panel on the right contains the actual test output (e.g. the `jtr` file).

During test execution you will see green and red bars appearing, notifying test passes/failures. It is possible to filter out the tests that show up in the left panel so that e.g. only failed test are displayed (that is done by clicking on the little circle with the `OK` label in the bottom panel); ignored tests can be filtered in a similar fashion.

If you click on the very first entry of the left panel (the one named 'Test Results'), some general information will be presented in the right panel - such as how many tests were run, how many pass/fail there were, etc. You will also see the URL to the location where test results have been written (this can be opened using your browser of choice).

### Creating new tests

To create a new jtreg test, simply create a new file under a jtreg test root. Then add a javadoc comment like this:

```
/*
 * @test
```

And press `CTRL + <Space>` - the IDE will show you possible completion suggestions for creating a positive/negative jtreg test. Once the right completion path is selected, a full jtreg header will be populated by the IDE. The IDE will then highlight the sections in the header that require user intervention (e.g. add summary in the `@summary` tag, etc.). Once you have finished typing in the input for a given section, just press enter and the IDE will move to the next section which requires manual intervention. Repeat the process until all required fields have been entered. At the end of this process, a new jtreg test will have been created.

## Miscellaneous

### What needs to be rebuilt before a test run?

The jtreg plugin is very flexible when it comes to defining the actions that should be taken by IntelliJ to rebuild the project before running/debugging a test. If your project does not use Ant, the default action associated with a jtreg test configuration is to simply run `Make` - which is the standard way in which IntelliJ builds a project. However, if your project uses Ant (this is the case with the JDK and the langtools IntelliJ projects), the plugin can launch any desired ant target before running a given test.

To select which Ant target to run before a jtreg test, simply go in the `File -> Settings -> jtreg` menu, and add ant targets to the drop down list. You can add, remove targets as well as changing order of existing targets.

### Dealing with bugs

In the unfortunate case you run into a plugin bug, you will notice a red mark in the bottom right part of the IDE. If you click on that icon, you will have the ability to show the stack trace associated with the error (and you will also be offered the option of disabling the plugin). If you want to report a bug against the jtreg plugin, we recommend that you copy the stack trace along with the IDE log file (this can be found under `Help -> Show Log in Files`) and submit them along with a description of the experienced buggy behavior. Please forward such bug reports to `jtreg-dev@openjdk.java.net`.
