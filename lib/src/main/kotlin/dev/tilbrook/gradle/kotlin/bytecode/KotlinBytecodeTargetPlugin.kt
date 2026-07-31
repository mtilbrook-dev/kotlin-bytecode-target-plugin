package dev.tilbrook.gradle.kotlin.bytecode

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

class KotlinBytecodeTargetPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val jvmTarget = providers
            .gradleProperty("dev.tilbrook.kotlin.bytecodeTarget")
            .getOrElse("17")
            .let { JavaVersion.toVersion(it) }

        jvmTarget(target, jvmTarget)

        pluginManager.withPlugin("com.android.application") {
            androidTarget<ApplicationExtension>(target, jvmTarget)
        }
        pluginManager.withPlugin("com.android.library") {
            androidTarget<LibraryExtension>(target, jvmTarget)
        }
    }

    private fun jvmTarget(project: Project, javaVersion: JavaVersion) {
        project.tasks.withType<KotlinJvmCompile> {
            project.logger.info("Configuring kotlin jdk-target with $javaVersion")
            compilerOptions {
                freeCompilerArgs.add("-Xjdk-release=${javaVersion.majorVersion}")
                jvmTarget.set(JvmTarget.valueOf("JVM_${javaVersion.majorVersion}"))
            }
        }

        // Kotlin requires the Java compatibility matches despite have no sources.
        project.tasks.withType<JavaCompile> {
            project.logger.info("Configuring java source & target compatibility with jdk $javaVersion")
            sourceCompatibility = javaVersion.toString()
            targetCompatibility = javaVersion.toString()
        }
    }

    private inline fun <reified T : CommonExtension> androidTarget(project: Project, javaVersion: JavaVersion) {
        project.logger.info("Configuring android source & target compatibility with jdk $javaVersion")
        project.tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                noJdk.set(false)
            }
        }
        project.extensions.configure<T> {
            compileOptions.apply {
                sourceCompatibility = javaVersion
                targetCompatibility = javaVersion
            }
        }
    }
}