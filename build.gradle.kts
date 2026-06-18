plugins {
    java
    id("com.gradleup.shadow") version "8.3.0"
}

group = "me.usainsrht.basiceconomy"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    compileOnly("me.clip:placeholderapi:2.11.5")
    compileOnly("org.mongodb:mongodb-driver-sync:5.0.0")
    
    // bStats
    implementation("org.bstats:bstats-bukkit:3.0.2")
    
    // HikariCP for SQL
    compileOnly("com.zaxxer:HikariCP:5.1.0")
}

java {
}

tasks {
    shadowJar {
        relocate("org.bstats", "me.usainsrht.basiceconomy.lib.bstats")
    }

    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
