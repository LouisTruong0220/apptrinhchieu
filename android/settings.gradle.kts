pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ⚠ KHÔNG thêm jitpack.io — Agent SDK lấy từ gói offline của hãng ở app/libs/
    }
}
rootProject.name = "SuKienCongNghiep2026"
include(":app")
