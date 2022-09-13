package net.corda.plugins.cpb2

import net.corda.plugins.cpk2.ALL_CORDAPPS_CONFIGURATION_NAME
import net.corda.plugins.cpk2.Attributor
import net.corda.plugins.cpk2.CordappExtension
import net.corda.plugins.cpk2.CordappPlugin
import net.corda.plugins.cpk2.SignJar.Companion.sign
import net.corda.plugins.cpk2.copyJarEnabledTo
import net.corda.plugins.cpk2.nested
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.tasks.bundling.Jar

@Suppress("Unused", "UnstableApiUsage")
class CpbPlugin : Plugin<Project> {

    companion object {
        private const val CPB_TASK_NAME = "cpb"
        private const val CPB_CONFIGURATION_NAME = CPB_TASK_NAME
        private const val CORDA_CPB_CONFIGURATION_NAME = "cordaCPB"
        private const val CPB_PACKAGING_CONFIGURATION_NAME = "cpbPackaging"
    }

    override fun apply(project: Project) {
        project.pluginManager.apply(CordappPlugin::class.java)
        val allCordappsConfiguration = project.configurations.getByName(ALL_CORDAPPS_CONFIGURATION_NAME)
        val attributor = Attributor(project.objects)

        val cpbConfiguration = project.configurations.create(CPB_CONFIGURATION_NAME)
            .setDescription("Additional CPK dependencies to include inside the CPB.")
            .extendsFrom(allCordappsConfiguration)
            .setVisible(false)
            .apply {
                isCanBeConsumed = false
                isCanBeResolved = false
            }

        val cpbPackaging = project.configurations.create(CPB_PACKAGING_CONFIGURATION_NAME)
            .setTransitive(false)
            .setVisible(false)
            .extendsFrom(cpbConfiguration).apply {
                isCanBeConsumed = false
            }

        project.configurations.create(CORDA_CPB_CONFIGURATION_NAME)
            .attributes(attributor::forCpb)
            .isCanBeResolved = false
        
        val cpkTask = project.tasks.named(JAR_TASK_NAME, Jar::class.java)
        val cpkPath = cpkTask.flatMap(Jar::getArchiveFile)
        val allCPKs = project.objects.fileCollection().from(cpkPath, cpbPackaging)
        val cpbTaskProvider = project.tasks.register(CPB_TASK_NAME, CpbTask::class.java) { cpbTask ->
            cpbTask.dependsOn(cpbPackaging.buildDependencies)
            cpbTask.from(allCPKs)
            val cordappExtension = project.extensions.findByType(CordappExtension::class.java)
                ?: throw GradleException("cordapp extension not found")
            cpbTask.inputs.nested("cordappSigning", cordappExtension.signing)

            // Basic configuration of the CPB task.
            cpbTask.destinationDirectory.convention(cpkTask.flatMap(Jar::getDestinationDirectory))
            cpbTask.archiveBaseName.convention(cpkTask.flatMap(Jar::getArchiveBaseName))
            cpbTask.archiveAppendix.convention(cpkTask.flatMap(Jar::getArchiveAppendix))
            cpbTask.archiveVersion.convention(cpkTask.flatMap(Jar::getArchiveVersion))

            cpbTask.doLast {
                if (cordappExtension.signing.enabled.get()) {
                    cpbTask.sign(cordappExtension.signing.options, cpbTask.archiveFile.get().asFile)
                }
            }

            // Disable this task if the jar task is disabled.
            project.gradle.taskGraph.whenReady { graph ->
                copyJarEnabledTo(cpbTask).execute(graph)
            }
        }

        with(project.artifacts) {
            add(ARCHIVES_CONFIGURATION, cpbTaskProvider)
            add(CORDA_CPB_CONFIGURATION_NAME, cpbTaskProvider)
        }
    }
}