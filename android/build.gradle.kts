plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "com.myapp.fastshare_plugin"
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
}