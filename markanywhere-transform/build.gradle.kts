plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("markanywhere.convention")
}

kotlin {

    sourceSets {

        commonMain {
            dependencies {
                api(project(":markanywhere-flow"))
                api(project(":markanywhere-api"))
            }
        }

        commonTest {
            dependencies {
                implementation(project(":markanywhere-render"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}
