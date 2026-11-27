plugins {
    java
}

group = "net.bailanya"
version = "1.0.0"

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
    compileOnly("net.momirealms:craft-engine-bukkit-proxy:26.9.2-SNAPSHOT")
    testImplementation(files(configurations.compileClasspath))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
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

// 测试服探针独立打包，使用与生产插件相同的编译依赖。
val acceptance = sourceSets.create("acceptance") {
    java.setSrcDirs(listOf("../tools/acceptance/src"))
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}
tasks.register<Jar>("acceptanceProbe") {
    from(acceptance.output)
    from("../tools/acceptance/src/plugin.yml")
    archiveFileName.set("CeBotaniaAcceptance.jar")
    destinationDirectory.set(layout.buildDirectory.dir("acceptance"))
}
