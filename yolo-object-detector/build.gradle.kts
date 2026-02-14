plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    namespace = "dev.anonymous.yoloobjectdetector"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.gpu)
    implementation(libs.tensorflow.lite.gpu.api)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.omarabushanb",
        artifactId = "yolo-object-detector",
        version = "1.0.3"
    )

    pom {
        name.set("YOLO Object Detector Android")
        description.set("Android YOLO object detection library using TensorFlow Lite")
        inceptionYear.set("2024")
        url.set("https://github.com/OmarAbuShanb/yolo-object-detector-android")

        licenses {
            license {
                name.set("Apache License 2.0")
                url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("OmarAbuShanb")
                name.set("Omar Abu Shanb")
                email.set("2200117700@gmail.com")
            }
        }

        scm {
            connection.set("scm:git:github.com/OmarAbuShanb/yolo-object-detector-android.git")
            developerConnection.set("scm:git:ssh://github.com/OmarAbuShanb/yolo-object-detector-android.git")
            url.set("https://github.com/OmarAbuShanb/yolo-object-detector-android")
        }
    }
}