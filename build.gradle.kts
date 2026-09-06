plugins {
    java
    alias(libs.plugins.spring.boot) apply false
}

// The `libs` version-catalog accessor only resolves in the root script's own scope, not inside
// the `subprojects` block below, so read what we need out of it here and close over the values.
val javaLanguageVersion = libs.versions.java.get().toInt()
val springBootBom = libs.spring.boot.bom

allprojects {
    group = "io.kessai"
    version = "0.1.0-SNAPSHOT"
}

// `include("platform:common-core")` also creates bare `:platform` and `:services` container
// projects. They hold no source, so configuring them would only produce empty jars. Only
// projects that declare their own build file are real modules.
configure(subprojects.filter { it.file("build.gradle.kts").exists() }) {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaLanguageVersion)
        }
    }

    dependencies {
        "implementation"(platform(springBootBom))
        "testImplementation"(platform(springBootBom))
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial", "-parameters"))
    }
}
