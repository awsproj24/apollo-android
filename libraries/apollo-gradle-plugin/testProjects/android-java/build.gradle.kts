import com.android.build.gradle.BaseExtension

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.apollo)
}

dependencies {
  add("implementation", libs.apollo.api)
}

configure<BaseExtension> {
  namespace = "com.example"

  compileSdkVersion(libs.versions.android.sdkversion.compile.get().toInt())

  defaultConfig {
    minSdkVersion(libs.versions.android.sdkversion.min.get())
    targetSdkVersion(libs.versions.android.sdkversion.target.get())
  }
}

java.toolchain {
  languageVersion.set(JavaLanguageVersion.of(11))
}


apollo {
  service("service") {
    packageName.set("com.example")
  }
}
