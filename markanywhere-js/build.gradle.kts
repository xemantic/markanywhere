plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

val isJvmOnlyBuild: Boolean by extra

kotlin {

    sourceSets {

        if (!isJvmOnlyBuild) {
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

}

if (!isJvmOnlyBuild) {
    // DOM tests require a browser environment - Node.js has no document object
    tasks.named("jsNodeTest") {
        enabled = false
    }
}
