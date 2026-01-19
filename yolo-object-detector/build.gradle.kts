import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
    id("signing")
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
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
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

val localProps = Properties().apply {
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        load(propsFile.inputStream())
    }
}

fun getPropertyOrEnv(key: String, envKey: String): String? =
    localProps.getProperty(key) ?: System.getenv(envKey)

// Maven Publishing Configuration
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.github.omarabushanb"
                artifactId = "yolo-object-detector"
                version = "1.0.0"

                pom {
                    name = "YOLO Object Detector Android"
                    description.set("Android YOLO object detection library using TensorFlow Lite")
                    url = "https://github.com/OmarAbuShanb/yolo-object-detector-android"

                    licenses {
                        license {
                            name = "The Apache License, Version 2.0"
                            url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        }
                    }

                    developers {
                        developer {
                            id = "OmarAbuShanb"
                            name = "Omar Abu Shanb"
                            email = "2200117700@gmail.com"
                        }
                    }

                    scm {
                        connection =
                            "scm:git:git://github.com/OmarAbuShanb/yolo-object-detector-android.git"
                        developerConnection =
                            "scm:git:ssh://github.com/OmarAbuShanb/yolo-object-detector-android.git"
                        url = "https://github.com/OmarAbuShanb/yolo-object-detector-android"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"
                url =
                    uri("https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC")
                credentials {
                    username = getPropertyOrEnv("centralUsername", "CENTRAL_USERNAME")
                    password = getPropertyOrEnv("centralPassword", "CENTRAL_PASSWORD")
                }
            }
        }
    }

    signing {
        val keyId = getPropertyOrEnv("signing.keyId", "SIGNING_KEY_ID")
        val password = getPropertyOrEnv("signing.password", "SIGNING_PASSWORD")
        val secretKeyRingFile = getPropertyOrEnv("signing.secretKeyRingFile", "SIGNING_SECRET_KEY_RING_FILE")

        if (keyId != null && password != null) {
            extra["signing.keyId"] = keyId
            extra["signing.password"] = password

            if (secretKeyRingFile != null) {
                extra["signing.secretKeyRingFile"] = secretKeyRingFile
            } else {
                val gpgKey = System.getenv("ORG_GRADLE_PROJECT_signingKey")
                if (gpgKey != null) {
                    useInMemoryPgpKeys(keyId, gpgKey, password)
                }
            }
        }

        sign(publishing.publications["release"])
    }
}
