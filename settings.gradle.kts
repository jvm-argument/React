rootProject.name = "react"

include(
    "common",
    "paper",
    "folia",
    "kits",
    "auth",
    "server"
)

dependencyResolutionManagement {

    versionCatalogs {

        create("libs") {

            from(files("libs.versions.toml"))

        }

    }

}
