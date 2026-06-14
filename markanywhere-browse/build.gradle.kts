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

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("markanywhere.convention")
}

val devBuild: Boolean by extra

kotlin {

    explicitApi()

    // This module's commonMain dependency `kdriver` (dev.kdriver:core) publishes
    // only jvm, js, and the desktop-native triples — no wasm, apple-mobile, or
    // android-native artifacts. So instead of allTargets() the full build
    // declares exactly the intersection kdriver supports.
    if (devBuild) {
        jvm()
    } else {
        jvm()
        js {
            browser()
            nodejs()
            binaries.library()
        }
        macosArm64()
        linuxX64()
        linuxArm64()
        mingwX64()
    }

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-api"))
                api(project(":markanywhere-dump"))
                api(libs.kdriver.core)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":markanywhere-test"))
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.io.core)
            }
        }

        jvmTest {
            dependencies {
                runtimeOnly(libs.slf4j.api)
                runtimeOnly(libs.logback.classic)
            }
        }

    }

}