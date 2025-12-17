plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

kotlin {

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
