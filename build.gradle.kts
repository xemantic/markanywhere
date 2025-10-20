@file:OptIn(ExperimentalWasmDsl::class, ExperimentalKotlinGradlePluginApi::class)

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.xemantic.gradle.conventions.License
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.power.assert)
    alias(libs.plugins.kotlinx.binary.compatibility.validator)
    alias(libs.plugins.dokka)
    alias(libs.plugins.versions)
    `maven-publish`
    signing
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.xemantic.conventions)
}

// TODO change the group
group = "com.xemantic.template"

// TODO fill up the details
xemantic {
    description = "A template repository for Xemantic's Kotlin multiplatform projects"
    inceptionYear = 2025
    license = License.APACHE
    developer(
        id = "morisil",
        name = "Kazik Pogoda",
        email = "morisil@xemantic.com"
    )
}

val releaseAnnouncementSubject = """🚀 ${rootProject.name} $version has been released!"""

val releaseAnnouncement = """
$releaseAnnouncementSubject

${xemantic.description}

${xemantic.releasePageUrl}
"""

val javaTarget = libs.versions.javaTarget.get()
val kotlinTarget = KotlinVersion.fromVersion(libs.versions.kotlinTarget.get())

repositories {
    mavenCentral()
}

kotlin {

    // TODO remove for a non-library project
    explicitApi()

    compilerOptions {
        apiVersion = kotlinTarget
        languageVersion = kotlinTarget
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xcontext-sensitive-resolution"
        )
        extraWarnings = true
        progressiveMode = true
    }

    jvm {
        // set up according to https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/
        compilerOptions {
            apiVersion = kotlinTarget
            languageVersion = kotlinTarget
            jvmTarget = JvmTarget.fromTarget(javaTarget)
            freeCompilerArgs.add("-Xjdk-release=$javaTarget")
            progressiveMode = true
        }
    }

    js {
        browser()
        nodejs()
        // TODO remove for a non-library project
        binaries.library()
    }

    wasmJs {
        browser()
        nodejs()
        d8()
        // TODO remove for a non-library project
        binaries.library()
    }

    wasmWasi {
        nodejs()
        // TODO remove for a non-library project
        binaries.library()
    }

    // native, see https://kotlinlang.org/docs/native-target-support.html
    // tier 1
    macosX64()
    macosArm64()
    iosSimulatorArm64()
    iosX64()
    iosArm64()

    // tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosX64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosX64()
    tvosArm64()

    // tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    mingwX64()
    watchosDeviceArm64()

    @OptIn(ExperimentalSwiftExportDsl::class)
    swiftExport {}

    sourceSets {

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.xemantic.kotlin.test)
            }
        }

    }

}

tasks {

    // skip tests which require XCode components to be installed
    named("tvosSimulatorArm64Test") { enabled = false }
    named("watchosSimulatorArm64Test") { enabled = false }

}

powerAssert {
    functions = listOf(
        "com.xemantic.kotlin.test.assert",
        "com.xemantic.kotlin.test.have"
    )
}

val unstableKeywords = listOf("alpha", "beta", "rc")

fun isNonStable(
    version: String
) = version.lowercase().let { normalizedVersion ->
    unstableKeywords.any {
        it in normalizedVersion
    }
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

// https://kotlinlang.org/docs/dokka-migration.html#adjust-configuration-options
dokka {
    pluginsConfiguration.html {
        footerMessage.set(xemantic.copyright)
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaGeneratePublicationHtml)
}

publishing {
    publications {
        withType<MavenPublication> {
            artifact(javadocJar)
            xemantic.configurePom(this)
        }
    }
}

jreleaser {
    project {
        description = xemantic.description
        copyright = xemantic.copyright
        license = xemantic.license!!.spxdx
        links {
            homepage = xemantic.homepageUrl
            documentation = xemantic.documentationUrl
        }
        authors = xemantic.authorIds
    }
    deploy {
        maven {
            mavenCentral {
                create("maven-central") {
                    active = Active.ALWAYS
                    url = "https://central.sonatype.com/api/v1/publisher"
                    applyMavenCentralRules = false
                    maxRetries = 240
                    stagingRepository(xemantic.stagingDeployDir.path)
                    // workaround: https://github.com/jreleaser/jreleaser/issues/1784
                    kotlin.targets.forEach { target ->
                        if (target !is KotlinJvmTarget) {
                            val nonJarArtifactId = if (target.platformType == KotlinPlatformType.wasm) {
                                "${name}-wasm-${target.name.lowercase().substringAfter("wasm")}"
                            } else {
                                "${name}-${target.name.lowercase()}"
                            }
                            artifactOverride {
                                artifactId = nonJarArtifactId
                                jar = false
                                verifyPom = false
                                sourceJar = false
                                javadocJar = false
                            }
                        }
                    }
                }
            }
        }
    }
    release {
        github {
            skipRelease = true // we are releasing through GitHub UI
            skipTag = true
            token = "empty"
            changelog {
                enabled = false
            }
        }
    }
    checksum {
        individual = false
        artifacts = false
        files = false
    }
    announce {
        webhooks {
            create("discord") {
                active = Active.ALWAYS
                message = releaseAnnouncement
                messageProperty = "content"
                structuredMessage = true
            }
        }
        linkedin {
            active = Active.ALWAYS
            subject = releaseAnnouncementSubject
            message = releaseAnnouncement
        }
        bluesky {
            active = Active.ALWAYS
            status = releaseAnnouncement
        }
    }
}
