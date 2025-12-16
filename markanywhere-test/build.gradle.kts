plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

kotlin {

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-flow"))
                api(libs.kotlinx.coroutines.core)
                implementation(libs.xemantic.kotlin.test)
                implementation(libs.kotlin.test)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }

    }

}
