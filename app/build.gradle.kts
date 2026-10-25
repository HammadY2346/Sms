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
        versionName = "0.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // release = "normal" (quiet). debug = "debug" (verbose + writes a log file).
        // Both are signed with the debug key so they install; signing is irrelevant for an Xposed module.
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("debug") }
    }
    buildFeatures { buildConfig = true }
    lint { abortOnError = false }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
