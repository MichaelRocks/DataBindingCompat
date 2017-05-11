/*
 * Copyright 2016 Michael Rozumyanskiy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.michaelrocks.databindingcompat.transform

import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.BaseExtension
import io.michaelrocks.databindingcompat.logging.getLogger
import io.michaelrocks.databindingcompat.processor.DataBindingCompatProcessor
import io.michaelrocks.databindingcompat.transform.TransformUnit.Format
import io.michaelrocks.databindingcompat.transform.TransformUnit.Status
import java.io.File
import java.util.EnumSet

class DataBindingCompatTransform(private val android: BaseExtension) : Transform() {
  private val logger = getLogger()

  override fun transform(invocation: TransformInvocation) {
    val transformationSet = TransformSet.create(invocation, android.bootClasspath)
    transformationSet.copyInputsToOutputs()
    DataBindingCompatProcessor(transformationSet).use { processor ->
      try {
        processor.process()
      } catch (exception: Exception) {
        throw TransformException(exception)
      }
    }
  }

  override fun getName(): String {
    return "dataBindingCompat"
  }

  override fun getInputTypes(): Set<QualifiedContent.ContentType> {
    return EnumSet.of(QualifiedContent.DefaultContentType.CLASSES)
  }

  override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
    return EnumSet.of(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
  }

  override fun getReferencedScopes(): MutableSet<in QualifiedContent.Scope> {
    return EnumSet.of(
        QualifiedContent.Scope.PROJECT,
        QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
        QualifiedContent.Scope.SUB_PROJECTS,
        QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
        QualifiedContent.Scope.PROVIDED_ONLY
    )
  }

  override fun isIncremental(): Boolean {
    return true
  }

  private fun TransformSet.copyInputsToOutputs() {
    units.forEach { unit ->
      when (unit.format) {
        Format.DIRECTORY -> unit.input.copyDirectoryTo(unit.output, unit.changes)
        Format.JAR -> unit.input.copyJarTo(unit.output, unit.changes)
      }
    }
  }

  private fun File.copyDirectoryTo(target: File, changes: Changes) {
    if (!changes.hasFileStatuses) {
      logger.info("Non-incremental directory change: {} -> {}", this, target)
      target.deleteRecursively()
      if (exists()) {
        copyRecursively(target)
      }
      return
    }

    logger.info("Incremental directory change: {} -> {}", this, target)
    target.mkdirs()
    changes.files.forEach { file ->
      if (file.isFile) {
        val status = changes.getFileStatus(file)
        val relativePath = file.toRelativeString(this)
        val targetFile = File(target, relativePath)
        file.applyChangesTo(targetFile, status)
      }
    }
  }

  private fun File.copyJarTo(target: File, changes: Changes) {
    logger.info("Jar change: {} -> {}", this, target)
    applyChangesTo(target, changes.status)
  }

  private fun File.applyChangesTo(target: File, status: TransformUnit.Status) {
    logger.debug("Incremental file change ({}): {} -> {}", status, this, target)
    when (status) {
      Status.UNCHANGED -> return
      Status.REMOVED -> target.delete()
      Status.ADDED -> copyTo(target, true)
      Status.CHANGED -> copyTo(target, true)
      Status.UNKNOWN -> applyChangesTo(target, if (exists()) Status.CHANGED else Status.REMOVED)
    }
  }
}
