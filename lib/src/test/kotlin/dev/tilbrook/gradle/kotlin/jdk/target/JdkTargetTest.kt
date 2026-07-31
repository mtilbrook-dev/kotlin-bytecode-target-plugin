package dev.tilbrook.gradle.kotlin.jdk.target

import org.gradle.api.JavaVersion
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JdkTargetTest {
  private companion object {
    const val TESTED_GRADLE_VERSION = "9.5.0"
  }

  private lateinit var buildFile: File

  private lateinit var gradleProperties: File

  @get:Rule
  val testProjectDir = TemporaryFolder()

  @Before
  fun setup() {
    val settingsFile = testProjectDir.newFile("settings.gradle.kts")
    settingsFile.writeText(settings())
    File("./src/testFixture").copyRecursively(testProjectDir.root)
    gradleProperties = testProjectDir.newFile("gradle.properties")
    buildFile = testProjectDir.newFile("build.gradle.kts")
  }

  @Test
  fun `android targeting java17 with compileSDK 36 emits Android removeFirst`() {
    verifyAndroidRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_17,
      compileSdk = 36,
      expectedSource = RemoveSource.Java,
    )
  }

  @Test
  fun `android targeting java21 with compileSDK 36 emits Java removeFirst`() {
    verifyAndroidRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_21,
      compileSdk = 36,
      expectedSource = RemoveSource.Java,
    )
  }

  @Test
  fun `android targeting java17 with compileSDK 34 emits Kotlin removeFirst`() {
    verifyAndroidRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_17,
      compileSdk = 34,
      expectedSource = RemoveSource.Kotlin,
    )
  }

  @Test
  fun `JVM targeting java17 emits Kotlin removeFirst`() {
    verifyJvmRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_17,
      expectedSource = RemoveSource.Kotlin,
    )
  }

  @Test
  fun `JVM targeting java21 emits Java removeFirst`() {
    verifyJvmRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_21,
      expectedSource = RemoveSource.Java,
    )
  }

  @Test
  fun `Kotlin Multiplatform JVM targeting java17 emits Kotlin removeFirst`() {
    verifyKmpRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_17,
      expectedSource = RemoveSource.Kotlin,
    )
  }

  @Test
  fun `Kotlin Multiplatform JVM targeting java21 emits Java removeFirst`() {
    verifyKmpRemoveFirst(
      bytecodeTarget = JavaVersion.VERSION_21,
      expectedSource = RemoveSource.Java,
    )
  }

  private fun verifyAndroidRemoveFirst(
    bytecodeTarget: JavaVersion,
    compileSdk: Int,
    expectedSource: RemoveSource,
  ) {
    setBytecodeTarget(bytecodeTarget)
    writeBuildFile(
      "com.android.library",
      configuration = androidLibraryConfiguration(compileSdk),
    )

    runBuild("assembleDebug")

    assertRemoveFirst(readBytecode(ProjectType.Android), expectedSource)
  }

  private fun verifyJvmRemoveFirst(
    bytecodeTarget: JavaVersion,
    expectedSource: RemoveSource,
  ) {
    setBytecodeTarget(bytecodeTarget)
    writeBuildFile("org.jetbrains.kotlin.jvm")

    runBuild("assemble")

    assertRemoveFirst(readBytecode(ProjectType.Jvm), expectedSource)
  }

  private fun verifyKmpRemoveFirst(
    bytecodeTarget: JavaVersion,
    expectedSource: RemoveSource,
  ) {
    setBytecodeTarget(bytecodeTarget)
    copyKmpFixture()
    writeBuildFile(
      "org.jetbrains.kotlin.multiplatform",
      configuration = """
        kotlin {
          jvm()
        }
      """.trimIndent(),
    )

    runBuild("assemble")

    assertRemoveFirst(readBytecode(ProjectType.Kmp), expectedSource)
  }

  private fun runBuild(task: String) {
    val result = GradleRunner.create()
      .withGradleVersion(TESTED_GRADLE_VERSION)
      .withPluginClasspath()
      .withProjectDir(testProjectDir.root)
      .withArguments("--info", task)
      .build()

    println(result.output)
  }

  private fun setBytecodeTarget(version: JavaVersion) {
    gradleProperties.writeText(
      """
        dev.tilbrook.kotlin.bytecodeTarget=${version}
        """.trimIndent()
    )
  }

  private fun copyKmpFixture() {
    File(testProjectDir.root, "src/main/kotlin").copyRecursively(
      File(testProjectDir.root, "src/jvmMain/kotlin")
    )
  }

  private fun readBytecode(projectType: ProjectType): String {
    val file = File(testProjectDir.root, "bytecode.out")

    val classFile = File(testProjectDir.root, projectType.classFilePath)

    check(classFile.exists()) {
      "Class file does not exist: ${classFile.absolutePath}"
    }

    val process = ProcessBuilder("javap", "-v", classFile.absolutePath)
      .redirectErrorStream(true)
      .redirectOutput(file)
      .start()

    val exitCode = process.waitFor()
    check(exitCode == 0) {
      "javap failed with exit code $exitCode:\n${file.readText()}"
    }

    return file.readText().also {
      println("bytecode:\n$it")
    }
  }

  private enum class RemoveSource {
    Kotlin,
    Java,
  }

  private fun assertRemoveFirst(
    bytecode: String,
    expectedSource: RemoveSource,
  ) {
    val kotlinRemoveFirst =
      "kotlin/collections/CollectionsKt.removeFirst:(Ljava/util/List;)Ljava/lang/Object;"
    val javaRemoveFirst = "InterfaceMethod java/util/List.removeFirst:()Ljava/lang/Object;"

    when (expectedSource) {
      RemoveSource.Kotlin -> {
        assertTrue("Expected Kotlin removeFirst invocation", bytecode.contains(kotlinRemoveFirst))
        assertFalse("Did not expect Java removeFirst invocation", bytecode.contains(javaRemoveFirst))
      }
      RemoveSource.Java -> {
        assertFalse("Did not expect Kotlin removeFirst invocation", bytecode.contains(kotlinRemoveFirst))
        assertTrue("Expected Java removeFirst invocation", bytecode.contains(javaRemoveFirst))
      }
    }
  }

  private fun settings(): String = """
        pluginManagement {
            repositories {
                google {
                    content {
                        includeGroupByRegex("com\\.android.*")
                        includeGroupByRegex("com\\.google.*")
                        includeGroupByRegex("androidx.*")
                    }
                }
                mavenCentral()
                gradlePluginPortal()
            }
        }
        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
            }
        }

        rootProject.name = "TestLibrary"
    """.trimIndent()

  private fun androidLibraryConfiguration(compileSdk: Int): String = """
    android {
      namespace = "dev.tilbrook.mylibrary"
      compileSdk = $compileSdk

      defaultConfig {
        minSdk = 24
      }
    }
  """.trimIndent()

  private fun writeBuildFile(vararg plugins: String, configuration: String = "") {
    val ids = plugins.joinToString("\n", prefix = "    ") {
      "id(\"$it\")"
    }
    """
      plugins {
    $ids
        id("dev.tilbrook.kotlin.bytecode-target")
      }
      $configuration
    """.trimIndent()
      .also { buildFile.writeText(it) }
  }

  private enum class ProjectType(val classFilePath: String) {
    Android(
      "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes/" +
          "dev/tilbrook/test/jvm/RemoveFirstKt.class",
    ),
    Jvm("build/classes/kotlin/main/dev/tilbrook/test/jvm/RemoveFirstKt.class"),
    Kmp("build/classes/kotlin/jvm/main/dev/tilbrook/test/jvm/RemoveFirstKt.class"),
  }
}