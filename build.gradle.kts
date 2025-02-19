import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import net.kyori.indra.IndraCheckstylePlugin
import net.kyori.indra.IndraPlugin
import net.kyori.indra.IndraPublishingPlugin
import net.ltgt.gradle.errorprone.ErrorPronePlugin
import com.adarshr.gradle.testlogger.TestLoggerPlugin

plugins {
    id("net.kyori.indra")
    id("net.kyori.indra.publishing")
    id("net.kyori.indra.checkstyle")
    id("ca.stellardrift.opinionated")
    id("com.github.johnrengelman.shadow")
    id("com.github.ben-manes.versions")
    id("net.ltgt.errorprone")
    id("com.adarshr.test-logger")
    id("jacoco")
}

group = "love.broccolai.tickets"
version = "6.0.0-SNAPSHOT"

subprojects {
    apply<ShadowPlugin>()
    apply<IndraPlugin>()
    apply<IndraCheckstylePlugin>()
    apply<IndraPublishingPlugin>()
    apply<ErrorPronePlugin>()
    apply<TestLoggerPlugin>()
    apply<JacocoPlugin>()

    repositories {
        mavenCentral()
        sonatype.ossSnapshots()

        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots")
        maven("https://repo.papermc.io/repository/maven-public")
        maven("https://nexus.velocitypowered.com/repository/maven-public/")
        maven("https://repo.broccol.ai/releases")
        maven("https://repo.broccol.ai/snapshots")
    }

    dependencies {
        errorprone(rootProject.libs.errorprone)

        testImplementation(rootProject.libs.mockito)
        testImplementation(rootProject.libs.truth.core)
        testImplementation(rootProject.libs.truth.java.eight)

        testImplementation(rootProject.libs.junit.api)
        testImplementation(rootProject.libs.junit.engine)
    }

    tasks {

        indra {
            gpl3OnlyLicense()
            publishReleasesTo("broccolai", "https://repo.broccol.ai/releases")

            javaVersions {
                target(17)
                testWith(17)
            }

            github("broccolai", "tickets") {
                ci(true)
                issues(true)
            }
        }

        jacocoTestReport {
            reports {
                html.outputLocation.set(layout.buildDirectory.dir("jacocoHtml"))
            }
        }

        testlogger {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
            showPassed = true
        }

        compileJava {
            options.compilerArgs.add("-parameters")
        }

        processResources {
            expand("version" to rootProject.version)
        }

    }
}
