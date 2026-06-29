plugins {
    java
    application
    // Fat/shaded JAR: `./gradlew shadowJar` -> build/libs/vgi-hl7-<ver>-all.jar
    id("com.gradleup.shadow") version "9.4.2"
}

group = "farm.query"
version = "0.1.0-SNAPSHOT"

repositories {
    // The VGI Java SDK is published to Maven Central as farm.query:vgi /
    // farm.query:vgirpc, so the build is fully self-contained — no mavenLocal,
    // no sibling checkout, no composite build.
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.compilerArgs.addAll(
        listOf(
            "-Xlint:all,-serial,-processing",
            "-parameters", // ScalarFn reflects parameter names off @Vector/@Const/@Setting
        )
    )
    options.encoding = "UTF-8"
}

dependencies {
    // VGI Java SDK from Maven Central. `vgi` is the worker/catalog API (published
    // as farm.query:vgi) and pulls in farm.query:vgirpc transitively; vgirpc is
    // declared explicitly because the code imports farm.query.vgirpc.* directly.
    //
    // HL7 v2.x is a pipe-delimited text format, so there is NO third-party
    // parser dependency: the parser is pure JDK (see Hl7Message). In particular
    // we deliberately do NOT depend on the HAPI HL7v2 library, whose licensing is
    // murky for commercial use. This keeps vgi-hl7 MIT and dependency-light.
    implementation("farm.query:vgi:0.9.0")
    implementation("farm.query:vgirpc:0.11.0")

    // slf4j-simple sends ALL log output to System.err. The stdio Arrow-IPC
    // transport owns System.out, so anything written to stdout corrupts the
    // stream and hangs the worker; routing logging to stderr keeps stdout clean.
    implementation("org.slf4j:slf4j-simple:2.0.16")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("farm.query.vgi.hl7.Main")
    applicationDefaultJvmArgs = listOf("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

tasks.shadowJar {
    archiveBaseName.set("vgi-hl7")
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes(
            "Main-Class" to "farm.query.vgi.hl7.Main",
            "Multi-Release" to "true",
            // Arrow's off-heap MemoryUtil needs java.nio reflectively opened. Bake
            // it into the manifest so a bare `java -jar vgi-hl7-all.jar` works as a
            // VGI LOCATION without the caller having to pass --add-opens.
            "Add-Opens" to "java.base/java.nio",
        )
    }
}

// Make `build` produce the fat jar.
tasks.named("build") {
    dependsOn(tasks.shadowJar)
}

// Regenerate the committed SQL E2E fixtures (test/sql/data/*.hl7) from the same
// canonical message builders the JUnit tests use. The Makefile `test-sql` target
// runs this before haybarn-unittest so fixtures are reproducible from source
// rather than opaque committed files that can drift.
tasks.register<JavaExec>("generateSqlFixtures") {
    group = "verification"
    description = "Generate test/sql/data/*.hl7 message fixtures."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("farm.query.vgi.hl7.SqlFixtureGenerator")
    args(layout.projectDirectory.dir("test/sql/data").asFile.absolutePath)
}
