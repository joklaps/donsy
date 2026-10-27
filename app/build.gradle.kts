// SPDX-License-Identifier: GPL-3.0-or-later

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing uses an upload key kept outside the repository (docs/release.md). Without
// keystore.properties the release build is unsigned: fine for checking R8, not for Play.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "be.kodeworks.donsy"
    compileSdk = 36

    defaultConfig {
        applicationId = "be.kodeworks.donsy"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // en-XA (long/accented text) and ar-XB (RTL) for layout testing.
            isPseudoLocalesEnabled = true
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    androidResources {
        // Builds the per-app language list from the values-<lang> folders.
        generateLocaleConfig = true
    }

    bundle {
        language {
            // The in-app language picker can choose any translation, so every language must be
            // installed; Play would otherwise only deliver the device's languages.
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's API 36 runtime reads FileDescriptor internals.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    lint {
        error += listOf("MissingTranslation", "MissingQuantity")
        warning += "ExtraTranslation"
        abortOnError = true
        // Lint's Kotlin (K2) analysis of unit-test sources crashed intermittently with an internal
        // error ("Error while resolving FirRegularClassImpl from RAW_FIR to
        // COMPILER_REQUIRED_ANNOTATIONS", a bug in lint). Test code needs no lint checks, so skip it.
        ignoreTestSources = true
    }
}

dependencies {
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

// Android lint does not see string literals in Compose code, so this check keeps
// user-visible text in string resources (see docs/i18n.md).
// Add "// i18n-ignore" to a line to allow a literal deliberately.
val checkHardcodedStrings = tasks.register("checkHardcodedStrings") {
    group = "verification"
    description = "Fails when user-visible text is hardcoded instead of using string resources."
    val sources = fileTree("src/main/java") { include("**/*.kt") }
    val report = layout.buildDirectory.file("reports/hardcoded-strings.txt")
    inputs.files(sources)
    outputs.file(report)
    doLast {
        // A quote not followed by another quote: empty strings (clearing a field) are fine.
        val literal = "\"(?!\")"
        val patterns = listOf(
            Regex("""\bText\(\s*$literal"""),
            Regex("""\b(text|contentDescription|label|title|placeholder|message|actionLabel)\s*=\s*$literal"""),
            Regex("""\.set(ContentTitle|ContentText|Ticker)\(\s*$literal"""),
        )
        val violations = sources.files.sortedBy { it.path }.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val hit = patterns.any { it.containsMatchIn(line) } && !line.contains("i18n-ignore")
                if (hit) "${file.path}:${index + 1}: ${line.trim()}" else null
            }
        }
        report.get().asFile.writeText(violations.joinToString("\n"))
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Hardcoded user-visible strings found; move them to res/values/strings.xml:\n" +
                    violations.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkHardcodedStrings)
}

// Donsy is offline-first (docs/spec.md): the release manifest, after libraries have merged theirs
// in, may only ask for these permissions. INTERNET is never allowed. A new permission needs a
// reason in the spec, then an entry here.
abstract class CheckPermissionsTask : DefaultTask() {
    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:Input
    abstract val allowed: SetProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val requested = Regex("""<uses-permission(?:-sdk-23)?\b[^>]*android:name="([^"]+)"""")
            .findAll(manifest.get().asFile.readText())
            .map { it.groupValues[1] }
            .toSortedSet()
        report.get().asFile.writeText(requested.joinToString("\n"))
        val unexpected = requested - allowed.get()
        if (unexpected.isNotEmpty()) {
            throw GradleException(
                "The release manifest asks for permissions that aren't allowed: $unexpected\n" +
                    "Donsy works offline; see the permission list in app/build.gradle.kts.",
            )
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val checkPermissions = tasks.register<CheckPermissionsTask>("check${name}Permissions") {
            group = "verification"
            description = "Fails when the merged ${variant.name} manifest asks for a permission that isn't allowed."
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            allowed.set(
                setOf(
                    "android.permission.POST_NOTIFICATIONS",
                    "android.permission.SCHEDULE_EXACT_ALARM",
                    "android.permission.RECEIVE_BOOT_COMPLETED",
                    // Added by androidx.core; signature-level, private to the app.
                    "be.kodeworks.donsy.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                ),
            )
            report.set(layout.buildDirectory.file("reports/permissions-${variant.name}.txt"))
        }
        tasks.named("check") { dependsOn(checkPermissions) }
    }
}
