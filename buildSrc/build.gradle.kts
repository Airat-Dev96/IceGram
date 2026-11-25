plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("telegramBuildPlugin") {
            id = "org.telegram.build-plugin"
            implementationClass = "org.telegram.plugin.TelegramBuildPlugin"
        }
        register("telegramBuildAppPlugin") {
            id = "org.telegram.build-app-plugin"
            implementationClass = "org.telegram.plugin.TelegramBuildAppPlugin"
        }
    }
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation("com.android.tools.build:gradle:9.1.1")
    implementation("com.android.tools:sdk-common:32.1.1")
    implementation("com.google.code.gson:gson:2.15.0")
    // Build tooling only; none of ASM or the fixture is packaged in the app.
    implementation("org.ow2.asm:asm:9.9")
    implementation("org.ow2.asm:asm-tree:9.9")
    testImplementation("org.ow2.asm:asm-util:9.9")
    testImplementation("org.ow2.asm:asm-analysis:9.9")
}

val walletCallbackFixture by configurations.creating
dependencies {
    walletCallbackFixture("com.google.android.gms:play-services-wallet:19.2.1@aar") {
        isTransitive = false
    }
}

tasks.register<JavaExec>("testWalletCallbacks") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.icegram.gradle.WalletLegacyCallbacksTest")
    inputs.files(walletCallbackFixture)
    doFirst {
        args(walletCallbackFixture.singleFile.absolutePath)
    }
}
