plugins {
    kotlin("jvm") version "1.9.23"
    `maven-publish`
}

group = "org.ndts"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "org.ndts"
            artifactId = "tttrlib"
            version = "1.0.0"
        }
    }
}