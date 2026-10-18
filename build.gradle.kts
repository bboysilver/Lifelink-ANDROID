// Override AGP's built-in Kotlin runtime to match the Compose compiler plugin.
buildscript {
  dependencies {
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
  }
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
}
