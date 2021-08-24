// Copyright (c) 2020-2021 Yinsen (Tesla) Zhang.
// Use of this source code is governed by the GNU GPLv3 license that can be found in the LICENSE file.
dependencies {
  api(project(":api"))
  implementation(project(":pretty"))
  implementation(project(":parser"))
  val deps: java.util.Properties by rootProject.ext
  implementation("org.commonmark", "commonmark", version = deps.getProperty("version.commonmark"))
  compileOnly("org.graalvm.truffle", "truffle-api", version = deps.getProperty("version.graalvm"))
  testImplementation("org.graalvm.truffle", "truffle-api", version = deps.getProperty("version.graalvm"))
  annotationProcessor("org.graalvm.truffle", "truffle-dsl-processor", version = deps.getProperty("version.graalvm"))
  testImplementation("org.junit.jupiter", "junit-jupiter", version = deps.getProperty("version.junit"))
  testImplementation("org.hamcrest", "hamcrest", version = deps.getProperty("version.hamcrest"))
  testImplementation(project(":cli"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.aya.gradle.GenerateVersionTask>("generateVersion") {
  basePackage = "org.aya"
  outputDir = genDir.resolve("org/aya/prelude")
}

idea.module.generatedSourceDirs.add(genDir)
sourceSets.main {
  java.srcDirs(genDir)
}

tasks.compileJava { dependsOn(generateVersion) }
tasks.sourcesJar { dependsOn(generateVersion) }

val cleanGenerated = tasks.register("cleanGenerated") {
  group = "build"
  genDir.deleteRecursively()
}

tasks.named("clean") { dependsOn(cleanGenerated) }

tasks.named<Test>("test") {
  // https://github.com/skinny85/graalvm-truffle-tutorial/blob/e86d2a91b7d1bba5c5ed3aa6e6c0fc6b605c0e38/build.gradle#L13
  // These arguments are required because of the JVM 11's module system -
  // we grant this module access to the packages from the Truffle API JAR
  jvmArgs = listOf(
    "--enable-preview",
    "-ea",
    "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api=ALL-UNNAMED",
    "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.nodes=ALL-UNNAMED",
    "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.dsl=ALL-UNNAMED",
    "--add-exports", "org.graalvm.truffle/com.oracle.truffle.api.frame=ALL-UNNAMED",
  )
  testLogging.showStandardStreams = true
  testLogging.showCauses = true
  inputs.dir(projectDir.resolve("src/test/resources"))
}
