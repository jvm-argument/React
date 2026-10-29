rootProject.name = "react"

include(
    "common",
    "paper",
    "folia",
    "collector",
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
