package io.michaelrocks.databindingcompat

import com.android.builder.model.Version

object PluginVersion {
  val major: Int
  val minor: Int
  val patch: Int
  val suffix: String

  init {
    val version = Version.ANDROID_GRADLE_PLUGIN_VERSION
    suffix = version.substringAfter('-', "")
    val prefix = version.substringBefore('-')
    val parts = prefix.split('.', limit = 3)
    major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
  }
}
