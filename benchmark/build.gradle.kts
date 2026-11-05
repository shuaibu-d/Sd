// Optional physical-device Macrobenchmark module for SD Studio (Apache-2.0 AndroidX Benchmark).
// Activated when running on a physical mid-range Android device with -PenableMacrobenchmark=true.
// In headless CI / local JVM builds, PerformanceAndStabilityTest runs automatically via testDebugUnitTest.
plugins {
    id("com.android.test")
}

android {
    namespace = "com.sdtech.studio.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true
}
