plugins {
    id("java")
    id("react.base-conventions")
    id("react.shadow-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.schedula.bukkit)
    implementation("com.google.code.gson:gson:2.11.0")
    compileOnly(libs.paper.api)
    compileOnly(libs.cloud.paper)
    compileOnly(libs.configurate.yaml)
    compileOnly(libs.actions)
    compileOnly(libs.functionalconfiguration)
    compileOnly(libs.textserializer.legacy)
    testImplementation(libs.paper.api)
}

sourceSets.main {
    java.srcDir(project(":paper").file("src/main/java"))
    resources.srcDir(project(":common").file("src/main/resources"))
}

tasks.named<ProcessResources>("processResources") {
    notCompatibleWithConfigurationCache("Expands the standalone server plugin descriptor")
    inputs.properties("version" to rootProject.version.toString(), "mainClass" to "com.g4vrk.react.server.ServerReactPlugin")
    filesMatching("plugin.yml") {
        expand("version" to rootProject.version.toString(), "mainClass" to "com.g4vrk.react.server.ServerReactPlugin")
        filter { line: String -> if (line == "permissions:") "permissions:\n  react.server.model:\n    default: true" else line }
    }
}
tasks.named<Jar>("jar") { enabled = false }
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("React-Server-2.0.jar")
    relocate("com.google.gson", "com.g4vrk.react.server.internal.gson")
}
