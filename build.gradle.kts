plugins {
    kotlin("jvm") version "2.3.20"
    id("com.typewritermc.module-plugin") version "2.1.0"
}

group = "btcrenaud"
version = "2.8"

val omniGuiVersion = "0.11"

base {
    archivesName.set("Typewriter-QuestCodexExtension-Public")
}

repositories {
    mavenCentral()
    maven("https://repo.bluecolored.de/releases")
    maven("https://maven.typewritermc.com/beta/")
    maven("https://maven.typewritermc.com/external")
    ivy {
        name = "omniGuiGitHubReleases"
        url = uri("https://github.com/RenaudRl/Typewriter-OmniGUIExtension/releases/download")
        patternLayout {
            artifact("[revision]/Typewriter-OmniGUIExtension-$omniGuiVersion.[ext]")
        }
        metadataSources {
            artifact()
        }
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.13.1")
    compileOnly("de.bluecolored:bluemap-api:2.7.3")
    compileOnly("com.flowpowered:flow-math:1.0.3")
    compileOnly("btcrenaud:Typewriter-OmniGUIExtension:v$omniGuiVersion@jar")
    compileOnly("com.typewritermc:QuestExtension:0.9.0")
}

typewriter {
    namespace = "renaud"

    extension {
        name = "QuestCodex"
        shortDescription = "Create a Quest Codex in TypeWriter"
        description = """Typewriter extension module providing additional entries for the Typewriter plugin ecosystem. Supports Paper and Folia server platforms with full feature parity. This module extends the core functionality with specialized entries. Compatible with the official Typewriter engine and designed for standalone use."""
        engineVersion = "0.9.0-beta-175"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
            dependency(namespace = "typewritermc", name = "Quest")
            dependency(namespace = "renaud", name = "GuiAndDialogs")
        }
        paper()
    }
}

kotlin {
    jvmToolchain(21)
}

// The engine and QuestExtension are compileOnly (the server provides them at runtime), which left
// them off the test classpath entirely. Tests need them to compile against the same types.
configurations["testImplementation"].extendsFrom(configurations["compileOnly"])

tasks.test {
    useJUnitPlatform()
}

