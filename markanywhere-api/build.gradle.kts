import con.xemantic.markanywhere.buildlogic.allTargets

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    id("markanywhere.convention")
}

val devBuild: Boolean by extra

kotlin {

    // jvm + browser-js in dev: markanywhere-js (and its js test chain) needs a
    // js variant of this module, so dev builds expose one. The full set in CI.
    if (devBuild) { jvm(); js { browser() } } else allTargets()

    sourceSets {

        commonMain {
            dependencies {
                api(libs.kotlinx.serialization.core)
                api(libs.kotlinx.serialization.json)
                implementation(libs.jetbrains.annotations)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}
