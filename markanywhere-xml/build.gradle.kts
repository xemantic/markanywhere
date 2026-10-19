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

import com.xemantic.markanywhere.buildlogic.allTargets

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.allopen)
    alias(libs.plugins.kotlinx.benchmark)
    id("markanywhere.convention")
}

val devBuild = extra["devBuild"] as Boolean

kotlin {

    explicitApi()

    if (devBuild) jvm() else allTargets()

    // JMH benchmarks comparing the XML parsers, in a compilation of their own
    // so that neither the published artifact nor the tests depend on JMH.
    jvm {
        compilations.create("benchmark") {
            associateWith(this@jvm.compilations.getByName("main"))
        }
    }

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-api"))
                api(project(":markanywhere-flow"))
            }
        }

        named("jvmBenchmark") {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
                implementation(libs.xemantic.kotlin.core)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":markanywhere-test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}

// JMH requires benchmark state classes to be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

// ./gradlew :markanywhere-xml:benchmark (full run), or
// ./gradlew :markanywhere-xml:smokeBenchmark (a quick sanity pass)
benchmark {
    targets {
        register("jvmBenchmark")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "ms"
            mode = "avgt"
            // Several forks, so that one JVM's unlucky JIT or GC decisions
            // show up as variance instead of as the result.
            advanced("jvmForks", 3)
        }
        register("smoke") {
            warmups = 2
            iterations = 3
            iterationTime = 500
            iterationTimeUnit = "ms"
            outputTimeUnit = "ms"
            mode = "avgt"
            param("records", 5000)
        }
    }
}
