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
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradleExtension
import org.jetbrains.kotlin.powerassert.gradle.PowerAssertGradlePlugin

@Suppress("unused") // used by gradle
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

    // `devBuild` selects a minimal, fast-to-build target set for local
    // development — defaults to true so a bare `./gradlew build` only touches
    // the dev targets each module declares (jvm, or js-browser for js-only
    // modules). CI passes `-PdevBuild=false` to build the full published set.
    // Each module reads it via `val devBuild: Boolean by extra` and branches its
    // own `kotlin { }` target declarations on it (the convention no longer
    // declares any target itself).
    val devBuild: Boolean = when ((findProperty("devBuild") as? String)?.lowercase()) {
        null, "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException(
            "devBuild must be 'true' or 'false', got: '${findProperty("devBuild")}'"
        )
    }

    extra.set("devBuild", devBuild)

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
        doConfigure(kotlinVersion, jvmTargetVersion)
    }

    // Skip the tvOS / watchOS simulator tests, whose Xcode simulator runtimes
    // are not installed on the build host (the task's `device` property probes
    // Xcode and throws "Xcode does not support simulator tests for
    // tvos_simulator_arm64" when run). The `enabled = false` MUST be applied
    // from `afterEvaluate`, NOT a plain `configureEach`: the Kotlin Gradle
    // plugin re-enables the simulator test task in its own `afterEvaluate`
    // (tvOS-sim counts as host-supported on Apple Silicon), overwriting an
    // earlier `enabled = false`. Our `afterEvaluate` runs after KGP's because
    // this convention plugin is applied after `kotlin.multiplatform` in each
    // module's `plugins { }` block. Once disabled, the task is skipped before
    // its `device` property is ever evaluated, so no Xcode probe happens.
    afterEvaluate {
        tasks.withType<KotlinNativeSimulatorTest>().matching {
            it.name == "tvosSimulatorArm64Test" || it.name == "watchosSimulatorArm64Test"
        }.configureEach { enabled = false }
    }
}

/**
 * Cross-cutting configuration applied to every multiplatform module: shared
 * compiler options, the jvm compiler target (configured lazily, so it applies
 * to a `jvm` target whenever a module declares one without forcing the target
 * to exist), and `explicitApi()`.
 *
 * No Kotlin target is declared here — each module declares its own targets in
 * its `kotlin { }` block, branching on the `devBuild` flag (read via
 * `val devBuild: Boolean by extra`):
 *
 *     kotlin {
 *         if (devBuild) jvm() else allTargets()   // standard module
 *         // js-only module:  if (devBuild) js { browser() } else allTargets()
 *         // browse:          if (devBuild) jvm() else { jvm(); jsTarget(); macosArm64(); ... }
 *         sourceSets { ... }
 *     }
 *
 * This keeps local builds fast (jvm, or js-browser, only) while CI builds the
 * full published set, and lets a module restrict its non-dev targets to what
 * its dependencies support — e.g. `markanywhere-browse`, whose `kdriver`
 * dependency publishes only jvm/js and the desktop-native triples, omits wasm
 * and the Apple-mobile / android-native targets.
 *
 * The `targetGroup` value is stashed on the extension so [allTargets] can honor
 * it; hand-listed targets are taken verbatim.
 */
fun KotlinMultiplatformExtension.doConfigure(
    kotlinVersion: KotlinVersion,
    jvmTargetVersion: JvmTarget,
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

    // Lazily configure the jvm compiler target for whichever module declares a
    // jvm target (most do via their devBuild branch; the js-only module does
    // not). configureEach is order-independent w.r.t. the module's target
    // declarations.
    targets.withType(KotlinJvmTarget::class.java).configureEach {
        compilerOptions {
            jvmTarget.set(jvmTargetVersion)
        }
    }

}

/**
 * Declares the full set of published targets: jvm, js, the two wasm flavours,
 * and every supported Kotlin/Native target. This is the `devBuild == false`
 * branch most modules use.
 *
 * Honors the `targetGroup` flag — under `targetGroup=js` only jvm + js are added
 * (skipping wasm/native so a JS-focused build configures fast). Modules whose
 * dependencies cannot cover the whole set list their targets by hand instead.
 */
@OptIn(ExperimentalWasmDsl::class)
fun KotlinMultiplatformExtension.allTargets() {

    jvm()

    js {
        browser()
        nodejs()
        binaries.library()
    }

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
