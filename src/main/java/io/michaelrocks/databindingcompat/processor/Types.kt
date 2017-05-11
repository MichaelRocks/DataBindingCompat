package io.michaelrocks.databindingcompat.processor

import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName

object Types {
  val APP_COMPAT_RESOURCES = getObjectTypeByInternalName("android/support/v7/content/res/AppCompatResources")
  val VIEW_DATA_BINDING = getObjectTypeByInternalName("android/databinding/ViewDataBinding")
  val VIEW = getObjectTypeByInternalName("android/view/View")
}
