rootProject.name = "markanywhere"

pluginManagement {
    includeBuild("build-logic")
}

include(
    "markanywhere-api",
    "markanywhere-flow",
    "markanywhere-render",
    "markanywhere-parse",
    "markanywhere-extract",
    "markanywhere-js",
    "markanywhere-transform",
    "markanywhere-test"
)
