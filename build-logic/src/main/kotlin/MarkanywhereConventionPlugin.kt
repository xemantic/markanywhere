/*
 * Copyright 2026 Kazimierz Pogoda / Xemantic
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package con.xemantic.markanywhere.buildlogic

import com.vanniktech.maven.publish.MavenPublishPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin

enum class TargetGroup {
    ALL,
    JS;

    companion object {
        fun fromValue(value: String): TargetGroup = when (value.lowercase()) {
            "all" -> ALL
            "js" -> JS
            else -> throw IllegalArgumentException(
                "targetGroup must be 'all' or 'js', got: '$value'"
            )
        }
    }
}

class MarkanywhereConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.doApply()
    }

}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
fun Project.doApply() {

    // Access version catalog
    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    val javaTargetVersion = libs.findVersion("javaTarget").get().toString()
    val kotlinTargetVersion = libs.findVersion("kotlinTarget").get().toString()
    val kotlinVersion = KotlinVersion.fromVersion(kotlinTargetVersion)
    val jvmTargetVersion = JvmTarget.fromTarget(javaTargetVersion)

    // Read jvmOnlyBuild property - default to true if not specified
    val jvmOnlyBuild = findProperty("jvmOnlyBuild") as? String
    val isJvmOnlyBuild: Boolean = when (jvmOnlyBuild?.lowercase()) {
        null, "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException("jvmOnlyBuild must be 'true' or 'false', got: '$jvmOnlyBuild'")
    }

    val targetGroupValue = findProperty("targetGroup") as? String ?: "all"
    val targetGroup = TargetGroup.fromValue(targetGroupValue)

    extra.set("isJvmOnlyBuild", isJvmOnlyBuild)

    plugins.apply(PowerAssertGradlePlugin::class.java)
    extensions.configure<PowerAssertGradleExtension> {
        functions.set(listOf(
            "com.xemantic.kotlin.test.assert",
            "com.xemantic.kotlin.test.have"
        ))
    }

    plugins.apply("org.jetbrains.dokka")
    plugins.apply(MavenPublishPlugin::class.java)

    tasks.withType<JavaCompile> {
        options.release.set(javaTargetVersion.toInt())
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.findByType<KotlinMultiplatformExtension>()?.apply {
        doConfigure(kotlinVersion, jvmTargetVersion, isJvmOnlyBuild, targetGroup)
    }

    if (!isJvmOnlyBuild && targetGroup == TargetGroup.ALL) {
        // skip tests which require XCode components to be installed
        tasks.named("tvosSimulatorArm64Test") { enabled = false }
        tasks.named("watchosSimulatorArm64Test") { enabled = false }
    }
}

@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget,
    isJvmOnlyBuild: Boolean,
    targetGroup: TargetGroup
) {

    compilerOptions {
        extraWarnings.set(true)
        progressiveMode.set(true)
        languageVersion.set(kotlinVersion)
        apiVersion.set(kotlinVersion)
        freeCompilerArgs.addAll(
            "-Xcontext-sensitive-resolution"
        )
    }

    jvm {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }

    explicitApi()

    if (!isJvmOnlyBuild) {
        js {
//        useEsModules()
            browser()
            nodejs()
            binaries.library()
            compilerOptions {
//            moduleKind.set(JsModuleKind.MODULE_ES)
                freeCompilerArgs.addAll(
//                "-Xcontext-parameters",
//                "-Xcontext-sensitive-resolution",
//                "-Xir-minimized-member-names",
//                "-Xir-dce",
//                "-Xir-generate-inline-anonymous-functions",
//                "-Xoptimize-generated-js",
//                "-Xes-arrow-functions",
//                "-Xklib-ir-inliner"
                )
            }
//        compilerOptions {
//            target.set("es2015")
//        }
        }

        if (targetGroup == TargetGroup.ALL) {
            wasmJs {
                browser()
                nodejs()
                //d8()
                binaries.library()
            }

            wasmWasi {
                nodejs()
                binaries.library()
            }

            // native, see https://kotlinlang.org/docs/native-target-support.html
            // tier 1
            macosArm64()
            iosSimulatorArm64()
            iosX64()
            iosArm64()

            // tier 2
            linuxX64()
            linuxArm64()
            watchosSimulatorArm64()
            watchosArm32()
            watchosArm64()
            tvosSimulatorArm64()
            tvosArm64()

            // tier 3
            androidNativeArm32()
            androidNativeArm64()
            androidNativeX86()
            androidNativeX64()
            mingwX64()
            watchosDeviceArm64()
        }
    }

}
