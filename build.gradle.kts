import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
}

allprojects {
    group = "ru.course.roguelike"
    version = "0.1.0-SNAPSHOT"
}

tasks.register<JacocoReport>("jacocoRootReport") {
    group = "verification"
    description = "Aggregated JaCoCo coverage report for all subprojects"

    dependsOn(subprojects.map { it.tasks.named<JacocoReport>("jacocoTestReport") })

    executionData.setFrom(
        subprojects.map { it.layout.buildDirectory.file("jacoco/test.exec") },
    )

    sourceDirectories.setFrom(
        subprojects.flatMap { sub ->
            sub.extensions.findByType<SourceSetContainer>()
                ?.getByName("main")
                ?.allSource
                ?.srcDirs
                ?: emptySet()
        },
    )

    classDirectories.setFrom(
        subprojects.map { it.layout.buildDirectory.dir("classes/kotlin/main") },
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/root/jacocoRootReport.xml"))
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/root/html"))
    }
}

tasks.named("check") {
    dependsOn(subprojects.map { it.tasks.named("check") })
    dependsOn("jacocoRootReport")
}
