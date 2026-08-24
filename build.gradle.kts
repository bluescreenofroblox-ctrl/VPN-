plugins {
    id("com.android.application") version "8.1.0" apply false
}

repositories {
    google()
    mavenCentral()
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
