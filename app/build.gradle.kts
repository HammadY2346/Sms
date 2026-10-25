plugins { id("com.android.application") }

android {
    namespace = "com.hammad.smslink"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.hammad.smslink"
        minSdk = 29
        targetSdk = 35
        // CI passes the run number so every build installs over the previous one.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    lint { abortOnError = false }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
