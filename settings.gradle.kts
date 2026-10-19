pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven("https://maven.architectury.dev/")
        // required for architectury
        maven("https://maven.neoforged.net/releases")
    }
}

rootProject.name = "tgbridge"

listOf(
    "common",
    "common-jvm21",
    "paper",
    "fabric-obf",
).forEach {
    include(it)
    project(":$it").projectDir = file("modules/$it")
}

