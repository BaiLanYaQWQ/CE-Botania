plugins {
    java
}

group = "net.bailanya"
version = "0.1.0"

// 编译期依赖全部 compileOnly：运行时由 CraftEngine 插件与 Paper 提供。
// paper-api 与 CE 版本对齐服务端（Leaves 26.3 / CraftEngine 26.9.2）。
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.momirealms.net/releases/")
    maven("https://repo.momirealms.net/snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.9-alpha")
    compileOnly("net.momirealms:craft-engine-core:26.9.2-SNAPSHOT")
    compileOnly("net.momirealms:craft-engine-bukkit:26.9.2-SNAPSHOT")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:none")
}

// 语言文件从 pack 同步进插件，控制台与指令复用同一份。
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    from(file("../pack/configuration/langs")) {
        into("lang/pack")
    }
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.jar {
    archiveFileName.set("CE-植物魔法-${version}.jar")
}
