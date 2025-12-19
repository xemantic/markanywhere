import org.jreleaser.model.Active

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.dokka)
    alias(libs.plugins.jreleaser)
    alias(libs.plugins.kotlin.multiplatform) apply false
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

allprojects {
    group = "com.xemantic.markanywhere"
    repositories {
        mavenCentral()
    }
}

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
