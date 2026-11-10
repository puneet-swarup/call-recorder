import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("jacoco")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.callrecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callrecorder"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        create("release") {
            val storeFileProp = localProps.getProperty("RELEASE_STORE_FILE")
            val storePasswordProp = localProps.getProperty("RELEASE_STORE_PASSWORD")
            val keyAliasProp = localProps.getProperty("RELEASE_KEY_ALIAS")
            val keyPasswordProp = localProps.getProperty("RELEASE_KEY_PASSWORD")
            val hasAllSigningProps = !storeFileProp.isNullOrBlank() &&
                !storePasswordProp.isNullOrBlank() &&
                !keyAliasProp.isNullOrBlank() &&
                !keyPasswordProp.isNullOrBlank()
            if (hasAllSigningProps) {
                storeFile = rootProject.file(storeFileProp!!)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxParallelForks = 1
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf("**/.git", "**/*.kt", "**/.brain.git")
        }
    }
}

jacoco {
    version = "0.8.12"
}

tasks.register<JacocoReport>("unitCoverageReport") {
    dependsOn(tasks.named("testDebugUnitTest"))
    executionData.from(fileTree(layout.buildDirectory.asFile) {
        include("jacoco/testDebugUnitTest.exec")
    })
    classDirectories.from(fileTree(layout.buildDirectory.asFile) {
        include("tmp/kotlin-classes/debug/**/*.class")
    })
    sourceDirectories.from(fileTree(project.projectDir){
        include("src/main/java/**/*.kt")
        exclude("src/main/java/**/R.kt")
    })
    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/unitCoverage"))
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito:mockito-core:5.14.0")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.21")
}

// Keep all Kotlin artifacts aligned with the Kotlin Gradle plugin (2.0.21).
// Test libraries compiled against a newer Kotlin emit metadata the 2.0 compiler
// cannot read ("incompatible version of Kotlin"), so constrain the whole group.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.21")
            because("Kotlin stdlib/test metadata must match the Kotlin Gradle plugin")
        }
    }
}

