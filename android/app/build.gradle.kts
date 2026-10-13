plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.roboworld.congnghiep"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.roboworld.congnghiep"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    kotlinOptions { jvmTarget = "11" }
    buildTypes { release { isMinifyEnabled = false } }
    // mp4/mp3/jpg đã nén sẵn — nén lại chỉ tốn thời gian build và bắt WebView giải nén trước khi phát
    androidResources { noCompress += listOf("html", "mp4", "mp3", "jpg") }
}

dependencies {
    /*
     * Agent SDK (sdk-0.4.7.aar + agent-base-0.2.10.aar) — GÓI OFFLINE của hãng. KHÔNG dùng JitPack:
     * bản trên đó ghi cứng sai tên gói dịch vụ, AgentCore im lặng không làm gì.
     * App này KHÔNG dùng mic/AI — chỉ khởi tạo SDK để TẮT bốn công tắc mặc định sai của hãng
     * (thanh thoại đè giao diện, robot tự bắt lời người đi ngang) và cắt tiếng AgentOS tự nói.
     * robotservice.jar cần Gson nhưng không đóng gói kèm — thiếu gson là app văng lúc connectServer.
     */
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
