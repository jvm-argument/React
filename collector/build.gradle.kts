plugins {
    id("java-library")
    id("react.base-conventions")
}

dependencies {
    compileOnly(project(":common"))
    compileOnly(libs.paper.api)
    compileOnly(libs.packetevents)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.functionalconfiguration)
    compileOnly(libs.actions)
    compileOnly(libs.schedula.common)
    compileOnly(libs.configurate.yaml)

    testImplementation(project(":common"))
    testImplementation(libs.paper.api)
    testImplementation(libs.packetevents)
    testImplementation(libs.configurate.yaml)
    testImplementation(libs.okhttp)
}

tasks.named<Jar>("jar") {
    archiveFileName.set("ReactDatasetCollector-2.0.jar")
}

tasks.named<ProcessResources>("processResources") {
    val addonVersion = "2.0"
    inputs.property("version", addonVersion)
    filesMatching("react-addon.yml") {
        filter { it.replace("2.4.3", addonVersion) }
    }
}
