plugins {
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("ru.course.roguelike.eval.EvalMainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":game-service"))
    implementation(project(":mcp-server"))
    implementation(project(":agent-runner"))
    implementation(project(":policy-agent-runner"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logback.classic)
}
