package io.michaelrocks.databindingcompat.processor

import io.michaelrocks.databindingcompat.logging.getLogger
import io.michaelrocks.databindingcompat.transform.TransformSet
import io.michaelrocks.databindingcompat.transform.TransformUnit
import io.michaelrocks.grip.GripFactory
import io.michaelrocks.grip.mirrors.Type
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import processor.ViewDataBindingClassPatcher
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
      logger.debug("Classpath:\n  {}", grip.fileRegistry.classpath().joinToString(separator = "\n  "))
    }

    if (Types.APP_COMPAT_RESOURCES !in grip.fileRegistry) {
      logger.info("AppCompatResources class not found. Aborting...")
      return
    }

    patchViewDataBindingClass()
  }

  private fun patchViewDataBindingClass() {
    val data = createPatchedViewDataBindingClass()
    val input = findViewDataBindingClassFile()
    val output = findTransformUnitForInputFile(input)
    savePatchedViewDataBindingClass(output, data)
  }

  private fun createPatchedViewDataBindingClass(): ByteArray {
    val data = grip.fileRegistry.readClass(Types.VIEW_DATA_BINDING)
    val reader = ClassReader(data)
    val writer =
        StandaloneClassWriter(reader, ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES, grip.classRegistry)
    val patcher = ViewDataBindingClassPatcher(writer)
    reader.accept(patcher, ClassReader.SKIP_FRAMES)
    return writer.toByteArray()
  }

  private fun findViewDataBindingClassFile(): File {
    val file = grip.fileRegistry.findFileForType(Types.VIEW_DATA_BINDING)
    return checkNotNull(file) { "Cannot find ViewDataBinding class" }
  }

  private fun findTransformUnitForInputFile(input: File): TransformUnit {
    return transformSet.units.first { it.input == input }
  }

  private fun savePatchedViewDataBindingClass(unit: TransformUnit, data: ByteArray) {
    when (unit.format) {
      TransformUnit.Format.DIRECTORY -> savePatchedViewDataBindingClassToDirectory(unit.output, data)
      TransformUnit.Format.JAR -> savePatchedViewDataBindingClassToJar(unit.output, data)
    }
  }

  private fun savePatchedViewDataBindingClassToDirectory(directory: File, data: ByteArray) {
    logger.info("Save patched ViewDataBinding to directory {}", directory)
    val file = File(directory, Types.VIEW_DATA_BINDING.toFilePath())
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
        val path = Types.VIEW_DATA_BINDING.toFilePath()
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

  override fun close() {
    grip.close()
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
