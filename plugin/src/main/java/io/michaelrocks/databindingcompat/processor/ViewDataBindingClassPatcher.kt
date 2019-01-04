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

import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.toAsmType
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.GeneratorAdapter
import org.objectweb.asm.commons.Method

class ViewDataBindingClassPatcher(
  visitor: ClassVisitor?,
  private val appCompatResourcesType: Type.Object
) : ClassVisitor(Opcodes.ASM5, visitor) {

  override fun visitMethod(
    access: Int,
    name: String,
    desc: String,
    signature: String?,
    exceptions: Array<out String>?
  ): MethodVisitor? {
    val visitor = super.visitMethod(access, name, desc, signature, exceptions)
    if (name == "getDrawableFromResource" && desc == "(Landroid/view/View;I)Landroid/graphics/drawable/Drawable;") {
      replaceImplementation(visitor, access, name, desc, APP_COMPAT_RESOURCES_GET_DRAWABLE_METHOD)
      return null
    }

    if (name == "getColorStateListFromResource" && desc == "(Landroid/view/View;I)Landroid/content/res/ColorStateList;") {
      replaceImplementation(visitor, access, name, desc, APP_COMPAT_RESOURCES_GET_COLOR_STATE_LIST_METHOD)
      return null
    }

    return visitor
  }

  private fun replaceImplementation(visitor: MethodVisitor?, access: Int, name: String, desc: String, method: Method) {
    GeneratorAdapter(visitor, access, name, desc).apply {
      visitCode()
      loadArg(0)
      invokeVirtual(Types.VIEW.toAsmType(), VIEW_GET_CONTEXT_METHOD)
      loadArg(1)
      invokeStatic(appCompatResourcesType.toAsmType(), method)
      returnValue()
      endMethod()
    }
  }

  companion object {
    val APP_COMPAT_RESOURCES_GET_DRAWABLE_METHOD =
      Method("getDrawable", "(Landroid/content/Context;I)Landroid/graphics/drawable/Drawable;")
    val APP_COMPAT_RESOURCES_GET_COLOR_STATE_LIST_METHOD =
      Method("getColorStateList", "(Landroid/content/Context;I)Landroid/content/res/ColorStateList;")
    val VIEW_GET_CONTEXT_METHOD =
      Method("getContext", "()Landroid/content/Context;")
  }
}
