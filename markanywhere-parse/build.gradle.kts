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
            }
        }

        commonTest {
            dependencies {
                implementation(project(":markanywhere-test"))
                implementation(project(":markanywhere-render"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
                implementation(libs.xemantic.kotlin.core)
            }
        }

    }

}
