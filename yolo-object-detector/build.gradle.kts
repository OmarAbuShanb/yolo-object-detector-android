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
    load(rootProject.file("local.properties").inputStream())
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "io.github.omarabushanb"
                artifactId = "yolo-object-detector"
                version = "1.0.1"

                pom {
                    name.set("YOLO Object Detector")
                    description.set("Android YOLO object detection library using TensorFlow Lite")
                    url.set("https://github.com/OmarAbuShanb/yolo-object-detector")

                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    developers {
                        developer {
                            id.set("OmarAbuShanb")
                            name.set("Omar Abu Shanb")
                            email.set("22001177oo@gmail.com")
                        }
                    }

                    scm {
                        connection.set("scm:git:github.com/OmarAbuShanb/yolo-object-detector.git")
                        developerConnection.set("scm:git:ssh://github.com/OmarAbuShanb/yolo-object-detector.git")
                        url.set("https://github.com/OmarAbuShanb/yolo-object-detector")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "MavenCentral"
                url = uri("https://central.sonatype.com/api/v1/publish")
                credentials {
                    username = localProps["centralUsername"] as String
                    password = localProps["centralPassword"] as String
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        localProps["signing.keyId"] as String,
        file(localProps["signing.secretKeyRingFile"] as String).readText(),
        localProps["signing.password"] as String
    )
    sign(publishing.publications)
}