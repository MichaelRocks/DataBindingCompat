package io.michaelrocks.databindingcompat.transform

import java.io.File

data class TransformUnit(
    val input: File,
    val output: File,
    val format: Format,
    val changes: Changes
) {
  enum class Format {
    DIRECTORY,
    JAR
  }

  enum class Status {
    UNKNOWN,
    UNCHANGED,
    ADDED,
    CHANGED,
    REMOVED
  }
}
