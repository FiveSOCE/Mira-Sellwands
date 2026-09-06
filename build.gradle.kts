import java.net.URI
import java.security.MessageDigest

plugins { java }

group = "gg.mira"
version = "0.1.4"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

val miraCoreVersion = "0.4.1"
val miraCoreSha256 = "4a20f538762bb550b4f8c359eb16945eee786ed0741ba60c0dbfc7e07e2249a9"
val miraCoreJar = layout.projectDirectory.file("libs/MiraCore-$miraCoreVersion.jar").asFile

val miraShopVersion = "0.1.12"
val miraShopSha256 = "a2b2299c5282b64f32c72df1f721cd90234b0171650c9ea475f242f3046453a3"
val miraShopJar = layout.projectDirectory.file("libs/MiraShop-$miraShopVersion.jar").asFile

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(file.readBytes()).joinToString("") { byte -> "%02x".format(byte) }
}

val downloadMiraCore by tasks.registering {
    doLast {
        if (miraCoreJar.exists() && sha256(miraCoreJar) == miraCoreSha256) return@doLast
        miraCoreJar.parentFile.mkdirs()
        URI("https://github.com/FiveSOCE/MIra-core/releases/download/v$miraCoreVersion/MiraCore-$miraCoreVersion.jar")
            .toURL().openStream().use { input ->
                miraCoreJar.outputStream().use { output -> input.copyTo(output) }
            }
        check(sha256(miraCoreJar) == miraCoreSha256) { "Downloaded MiraCore JAR failed SHA-256 verification" }
    }
}

val downloadMiraShop by tasks.registering {
    doLast {
        if (miraShopJar.exists() && sha256(miraShopJar) == miraShopSha256) return@doLast
        miraShopJar.parentFile.mkdirs()
        URI("https://github.com/FiveSOCE/Mira-shop/releases/download/v$miraShopVersion/MiraShop-$miraShopVersion.jar")
            .toURL().openStream().use { input ->
                miraShopJar.outputStream().use { output -> input.copyTo(output) }
            }
        check(sha256(miraShopJar) == miraShopSha256) { "Downloaded MiraShop JAR failed SHA-256 verification" }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { exclude(group = "org.bukkit", module = "bukkit") }
    compileOnly(files(miraCoreJar))
    compileOnly(files(miraShopJar))
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadMiraCore, downloadMiraShop)
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar { archiveFileName.set("MiraSellWands-${project.version}.jar") }


tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
