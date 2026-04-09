plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

group = "com.miladnalbandi"
version = "1.1.8"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("commons-codec:commons-codec:1.16.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("zpl-renderer")
                description.set("A pure-JVM ZPL II label renderer — converts ZPL code to BufferedImage")
                url.set("https://github.com/Milimarty/zpl-renderer")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                developers {
                    developer {
                        id.set("Milimarty")
                        name.set("Milad Nalbandi")
                        email.set("m.nalbandi.r@gmail.com")
                    }
                }
            }
        }
    }
}
