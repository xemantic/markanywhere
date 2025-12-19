plugins {
    `kotlin-dsl`
}

val javaTarget = libs.versions.javaTarget.get()

java {
    val version = JavaVersion.toVersion(javaTarget)
    sourceCompatibility = version
    targetCompatibility = version
}

tasks.compileJava {
    options.release = javaTarget.toInt()
}

kotlin {
    compilerOptions {
        val specifiedJvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(javaTarget)
        jvmTarget = specifiedJvmTarget
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.power.assert)
    implementation(libs.maven.publish.plugin)
}

gradlePlugin {
    plugins {
        register("MarkanywhereConventionPlugin") {
            id = "markanywhere.convention"
            implementationClass = "con.xemantic.markanywhere.buildlogic.MarkanywhereConventionPlugin"
        }
    }
}
