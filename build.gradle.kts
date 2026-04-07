plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.bluecolored.de/releases")
    flatDir {
        dir("libs")
    }
}
dependencies {
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation(kotlin("reflect"))
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("com.flowpowered:flow-math:1.0.3")
}

group = "btc.renaud"
version = "0.2.0"

typewriter {
    namespace = "renaud"

    extension {
        name = "QuestCodex"
        shortDescription = "Create a Quest Codex in TypeWriter"
        description = """
            |A quest codex for Typewriter that allows players to view and manage their quests
            |Multiples menus, organized by status and tracking progress with quest categories.
            """.trimMargin()
        engineVersion = "0.9.0-beta-172"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            dependency("typewritermc", "Quest")
            paper()
        }
    }
}

kotlin {
    jvmToolchain(21)
}



