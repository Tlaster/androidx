plugins {
    kotlin("multiplatform") version "2.3.20"
    id("com.google.devtools.ksp") version "2.3.9"
}

repositories {
    maven(providers.gradleProperty("snapshotRepository").get()) {
        content { includeGroupByRegex("moe\\.tlaster\\.androidx\\..*") }
    }
    google()
    mavenCentral()
}

kotlin {
    mingwX64()
    sourceSets {
        commonMain {
            // Reuse the port's database fixture; KSP comes from the official release.
            kotlin.srcDir("../../../room3/integration-tests/mingwtestapp/src/commonMain/kotlin")
            dependencies {
                implementation("moe.tlaster.androidx.room3:room3-runtime:3.0.3-mingw-SNAPSHOT")
                implementation("moe.tlaster.androidx.room3:room3-paging:3.0.3-mingw-SNAPSHOT")
                implementation("moe.tlaster.androidx.sqlite:sqlite-bundled:2.7.1-mingw-SNAPSHOT")
                implementation("moe.tlaster.androidx.datastore:datastore-core-okio:1.3.0-alpha11-mingw-SNAPSHOT")
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }
    }
}

dependencies { add("kspMingwX64", "androidx.room3:room3-compiler:3.0.3") }

// Catch a split dependency graph before publishing or accepting a remote snapshot.
tasks.register("verifyForkDependencies") {
    doLast {
        val components = configurations.getByName("mingwX64CompileKlibraries").resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id }.toSet()
        val groups = setOf("androidx.room3", "androidx.sqlite", "androidx.datastore")
        check(components.none { it.group in groups }) { "Upstream storage dependency leaked into MinGW graph" }
        check(components.count { it.group.startsWith("moe.tlaster.androidx.") } == 9) {
            "Expected all nine fork libraries in the MinGW dependency graph"
        }
    }
}
