repositories {
     mavenCentral()
}
dependencies {
    implementation("com.typewritermc:QuestExtension:0.9.0")
    implementation(kotlin("reflect"))
}

plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.0.0"
}

group = "btc.renaud"
version = "0.1.1"

typewriter {
    namespace = "renaud"

    extension {
        name = "QuestCodex"
        shortDescription = "Create a Quest Codex in TypeWriter"
        description = """
            |A quest codex for Typewriter that allows players to view and manage their quests
            |Multiples menus, organized by status and tracking progress with quest categories.
            """.trimMargin()
        engineVersion = "0.9.0-beta-167"
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
