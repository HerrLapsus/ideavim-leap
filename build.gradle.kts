plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jetbrains.intellij.platform") version "2.0.1"
}

group = "com.herrlapsus"
version = "0.2.0"



repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

// Use Java toolchain instead of sourceCompatibility/targetCompatibility
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure Kotlin to use the same toolchain and modern compiler options
kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2")

        instrumentationTools()

        plugin("IdeaVim", "2.27.0")
    }
}

tasks {
    patchPluginXml {
        sinceBuild.set("242")
        untilBuild.set("*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
