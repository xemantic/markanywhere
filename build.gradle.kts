import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
//import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.dokka)
    //alias(libs.plugins.jreleaser)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.xemantic.conventions)
}

group = "com.xemantic.markanywhere"

xemantic {
    description = "Stream Markdown or Markup document formats as interchangeable hierarchical streams of events"
    inceptionYear = "2025"
//    applyAllConventions()
    applyAxTestReporting()
    applySignBeforePublishing()
//    applyJarManifests()
    applyReportOnlyStableDependencyUpdates()
    applyJReleaserConventions()
}

fun MavenPomDeveloperSpec.projectDevs() {
    developer {
        id = "morisil"
        name = "Kazik Pogoda"
        url = "https://github.com/morisil"
    }
}

// Capture xemantic extension values for use in subprojects
val projectDescription = xemantic.description
val projectInceptionYear = xemantic.inceptionYear
val gitHubAccount = xemantic.gitHubAccount
val organizationName = xemantic.organization
val organizationUrl = xemantic.organizationUrl

allprojects {
    group = "com.xemantic.markanywhere"
    repositories {
        mavenCentral()
    }
}

subprojects {

    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<MavenPublishBaseExtension> {

            configure(
                KotlinMultiplatform(
                    javadocJar = JavadocJar.Dokka("dokkaGenerateHtml"),
                    sourcesJar = true
                )
            )

            signAllPublications()

            publishToMavenCentral(
                automaticRelease = true,
                validateDeployment = false // for kotlin multiplatform projects it might take a while (>900s)
            )

            coordinates(
                groupId = group.toString(),
                artifactId = project.name,
                version = version.toString()
            )

            pom {

                name = project.name
                description = projectDescription
                inceptionYear = projectInceptionYear
                url = "https://github.com/${gitHubAccount}/${rootProject.name}"

                organization {
                    name = organizationName
                    url = organizationUrl
                }

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }

                scm {
                    url = "https://github.com/${gitHubAccount}/${rootProject.name}"
                    connection = "scm:git:git://github.com/${gitHubAccount}/${rootProject.name}.git"
                    developerConnection = "scm:git:ssh://git@github.com/${gitHubAccount}/${rootProject.name}.git"
                }

                ciManagement {
                    system = "GitHub"
                    url = "https://github.com/${gitHubAccount}/${rootProject.name}/actions"
                }

                issueManagement {
                    system = "GitHub"
                    url = "https://github.com/${gitHubAccount}/${rootProject.name}/issues"
                }

                developers {
                    projectDevs()
                }

            }

        }
    }

}


/*
val releaseAnnouncementSubject = """🚀 ${rootProject.name} $version has been released!"""
val releaseAnnouncement = """
$releaseAnnouncementSubject

${xemantic.description}

${xemantic.releasePageUrl}
""".trim()

jreleaser {

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
*/