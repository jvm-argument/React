rootProject.name = "react"

include(
    "common",
    "paper",
    "folia",
    "kits",
    "auth"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
