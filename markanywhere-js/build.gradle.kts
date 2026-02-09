plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

kotlin {

    sourceSets {

        jsMain {
            dependencies {
                api(project(":markanywhere-flow"))
                api(project(":markanywhere-api"))
                api(libs.kotlinx.coroutines.core)
            }
        }

        jsTest {
            dependencies {
                implementation(project(":markanywhere-test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}

val jvmOnlyBuild = findProperty("jvmOnlyBuild") as? String
val isJvmOnlyBuild: Boolean = (jvmOnlyBuild == null) || (jvmOnlyBuild.lowercase() == "true")

if (!isJvmOnlyBuild) {
    // bugfix: resolves gradle warnings which became errors in gradle 9.2, can be removed in the future
    tasks.named("jsBrowserProductionLibraryDistribution") {
        dependsOn(
            "jsTestTestDevelopmentExecutableCompileSync"
        )
    }

    // DOM tests require a browser environment - Node.js has no document object
    tasks.named("jsNodeTest") {
        enabled = false
    }
}
