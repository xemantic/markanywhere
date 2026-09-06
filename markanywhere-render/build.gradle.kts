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

import con.xemantic.markanywhere.buildlogic.allTargets

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

val devBuild = extra["devBuild"] as Boolean

kotlin {

    explicitApi()

    // jvm + browser-js in dev: markanywhere-parse's js test chain (and thus
    // markanywhere-js) needs a js variant of this module, so dev builds expose
    // one. The full set in CI.
    if (devBuild) { jvm(); js { browser() } } else allTargets()

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-api"))
                api(project(":markanywhere-flow"))
                implementation(libs.xemantic.kotlin.core)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}
