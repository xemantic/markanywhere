/*
 * Copyright (c) 2025. Kazimierz Pogoda / Xemantic. All rights reserved.
 *
 * This code is part of the "golem-xiv" project, a cognitive AI agent.
 * Unauthorized reproduction or distribution is prohibited.
 */

package con.xemantic.markanywhere.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin

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

    plugins.apply(PowerAssertGradlePlugin::class.java)
    extensions.configure<PowerAssertGradleExtension> {
        functions.set(listOf(
            "com.xemantic.kotlin.test.assert",
            "com.xemantic.kotlin.test.have"
        ))
    }

    tasks.withType<JavaCompile> {
        options.release.set(javaTargetVersion.toInt())
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.findByType<KotlinMultiplatformExtension>()?.apply {
        doConfigure(kotlinVersion, jvmTargetVersion)
    }

    extensions.findByType<KotlinJvmExtension>()?.apply {
        doConfigure(kotlinVersion, jvmTargetVersion)
    }

}

fun KotlinMultiplatformExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget
) {

    jvm {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
            configureCommons(kotlinVersion)
        }
    }

    explicitApi()

    js {
//        useEsModules()
        browser()
        binaries.library()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_ES)
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

    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    configureCommons(kotlinVersion)
                }
            }
        }
    }
}

fun KotlinJvmExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget
) {
    sourceSets.configureEach {
        kotlin {
            compilerOptions {
                configureCommons(kotlinVersion)
            }
        }
    }
    compilerOptions {
        jvmTarget.set(jvmTargetVersion)
        configureCommons(kotlinVersion)
    }
}

fun KotlinCommonCompilerOptions.configureCommons(
    kotlinVersion: KotlinVersion
) {
    extraWarnings.set(true)
    progressiveMode.set(true)
    languageVersion.set(kotlinVersion)
    apiVersion.set(kotlinVersion)
    freeCompilerArgs.set(
        listOf(
            "-Xcontext-sensitive-resolution",
        )
    )
    optIn.addAll(
        "kotlin.time.ExperimentalTime"
    )
}
