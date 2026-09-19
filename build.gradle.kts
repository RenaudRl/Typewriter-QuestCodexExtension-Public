plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.2.0"
}

group = "btcrenaud"
version = "2.14"

// Resolved from the published OmniGUI release, not from a sibling project: this repository is
// cloned on its own, so a project dependency would leave it unbuildable outside the workspace.
val omniGuiVersion = "0.18"

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
    // Adventure reaches compilation through paper-api, and compileOnly carries no transitives to
    // the test runtime — so a test that exercises the real MiniMessage-to-JSON conversion, rather
    // than hand-written JSON, needs it named here. Versions match what paper-api 1.21.11 resolves:
    // the point of the test is to see the serialization the server actually performs.
    testImplementation("net.kyori:adventure-text-minimessage:5.1.1")
    testImplementation("net.kyori:adventure-text-serializer-gson:5.1.1")
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
        engineVersion = "0.9.0-beta-177"
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

