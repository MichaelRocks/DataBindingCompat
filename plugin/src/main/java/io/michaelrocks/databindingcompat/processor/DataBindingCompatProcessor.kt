/*
 * Copyright 2017 Michael Rozumyanskiy
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

package io.michaelrocks.databindingcompat.processor

import io.michaelrocks.databindingcompat.logging.getLogger
import io.michaelrocks.databindingcompat.transform.TransformSet
import io.michaelrocks.databindingcompat.transform.TransformUnit
import io.michaelrocks.grip.GripFactory
import io.michaelrocks.grip.mirrors.Type
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest


class DataBindingCompatProcessor(private val transformSet: TransformSet) : Closeable {
  private val logger = getLogger()
  private val grip = GripFactory.create(transformSet.getClasspath())

  fun process() {
    logger.info("Starting DataBindingCompat")

    if (logger.isDebugEnabled) {
      transformSet.dump()
      logger.debug("Classpath:\n  {}", grip.fileRegistry.classpath().joinToString(separator = "\n  "))
    }

    if (Types.ANDROIDX_APP_COMPAT_RESOURCES in grip.fileRegistry) {
      if (Patch(Types.ANDROIDX_VIEW_DATA_BINDING, Types.ANDROIDX_APP_COMPAT_RESOURCES).maybeApply()) {
        logger.info("Patched {} successfully", Types.ANDROIDX_VIEW_DATA_BINDING.className)
        return
      }
    }

    if (Types.SUPPORT_APP_COMPAT_RESOURCES in grip.fileRegistry) {
      if (Patch(Types.SUPPORT_VIEW_DATA_BINDING, Types.SUPPORT_APP_COMPAT_RESOURCES).maybeApply()) {
        logger.info("Patched {} successfully", Types.SUPPORT_VIEW_DATA_BINDING.className)
        return
      }
    }

    logger.info("AppCompatResources class not found. Aborting...")
  }

  private fun TransformSet.dump() {
    logger.debug("Transform set:")
    logger.debug("  Units:\n    {}", units.joinToString(separator = "\n    "))
    logger.debug("  Referenced units:\n    {}", referencedUnits.joinToString(separator = "\n    "))
    logger.debug("  Boot classpath:\n    {}", bootClasspath.joinToString(separator = "\n    "))
  }

  override fun close() {
    grip.close()
  }

  private inner class Patch(
      private val viewDataBindingType: Type.Object,
      private val appCompatResourcesType: Type.Object
  ) {

    fun maybeApply(): Boolean {
      val input = findViewDataBindingClassFile()
      if (input == null) {
        logger.info("ViewDataBinding class not found. Aborting...")
        return false
      }

      val unit = findTransformUnitForInputFile(input)
      if (unit == null) {
        logger.info("ViewDataBinding class cannot be transformed. Aborting...")
        return false
      }

      logger.info("Patching ViewDataBinding.class: {}", unit)
      val data = createPatchedViewDataBindingClass()
      savePatchedViewDataBindingClass(unit, data)
      return true
    }

    private fun createPatchedViewDataBindingClass(): ByteArray {
      val data = grip.fileRegistry.readClass(viewDataBindingType)
      val reader = ClassReader(data)
      val writer = StandaloneClassWriter(reader, ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, grip.classRegistry)
      val patcher = ViewDataBindingClassPatcher(writer, appCompatResourcesType)
      reader.accept(patcher, ClassReader.SKIP_FRAMES)
      return writer.toByteArray()
    }

    private fun findViewDataBindingClassFile(): File? {
      return grip.fileRegistry.findFileForType(viewDataBindingType)
    }

    private fun findTransformUnitForInputFile(input: File): TransformUnit? {
      val canonicalInput = input.canonicalFile
      return transformSet.units.firstOrNull { it.input.canonicalFile == canonicalInput }
    }

    private fun savePatchedViewDataBindingClass(unit: TransformUnit, data: ByteArray) {
      when (unit.format) {
        TransformUnit.Format.DIRECTORY -> savePatchedViewDataBindingClassToDirectory(unit.output, data)
        TransformUnit.Format.JAR -> savePatchedViewDataBindingClassToJar(unit.output, data)
      }
    }

    private fun savePatchedViewDataBindingClassToDirectory(directory: File, data: ByteArray) {
      logger.info("Save patched ViewDataBinding to directory {}", directory)
      val file = File(directory, viewDataBindingType.toFilePath())
      file.mkdirs()
      file.writeBytes(data)
    }

    private fun savePatchedViewDataBindingClassToJar(jar: File, data: ByteArray) {
      logger.info("Save patched ViewDataBinding to jar {}", jar)
      val temporary = createTempFile(jar.name, "tmp")
      try {
        savePatchedViewDataBindingClassToJar(jar, temporary, data)
        temporary.inputStream().buffered().use { jarInputStream ->
          jar.outputStream().buffered().use { jarOutputStream ->
            jarInputStream.copyTo(jarOutputStream)
          }
        }
      } finally {
        if (!temporary.delete()) {
          logger.warn("Cannot delete a temporary file {}", temporary)
        }
      }
    }

    private fun savePatchedViewDataBindingClassToJar(source: File, target: File, data: ByteArray) {
      source.inputStream().buffered().jar().use { jarInputStream ->
        target.outputStream().buffered().jar(jarInputStream.manifest).use { jarOutputStream ->
          val path = viewDataBindingType.toFilePath()
          jarInputStream.entries().filterNot { it.name == path }.forEach { jarEntry ->
            jarOutputStream.putNextEntry(jarEntry)
            jarInputStream.copyTo(jarOutputStream)
            jarInputStream.closeEntry()
            jarOutputStream.closeEntry()
          }

          jarOutputStream.putNextEntry(JarEntry(path))
          jarOutputStream.write(data)
          jarOutputStream.closeEntry()
        }
      }
    }

    private fun Type.Object.toFilePath(): String {
      return "$internalName.class"
    }

    private fun InputStream.jar(verify: Boolean = true): JarInputStream {
      return JarInputStream(this, verify)
    }

    private fun OutputStream.jar(manifest: Manifest? = null): JarOutputStream {
      return if (manifest != null) JarOutputStream(this, manifest) else JarOutputStream(this)
    }

    private fun JarInputStream.entries(): Sequence<JarEntry> {
      return generateSequence { nextJarEntry }
    }
  }

  companion object {
    private fun TransformSet.getClasspath(): List<File> {
      val classpath = ArrayList<File>(units.size + referencedUnits.size + bootClasspath.size)
      units.mapTo(classpath) { it.input }
      referencedUnits.mapTo(classpath) { it.input }
      classpath += bootClasspath
      return classpath
    }
  }
}
