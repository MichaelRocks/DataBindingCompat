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

import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName

object Types {
  val APP_COMPAT_RESOURCES = getObjectTypeByInternalName("android/support/v7/content/res/AppCompatResources")
  val VIEW_DATA_BINDING = getObjectTypeByInternalName("android/databinding/ViewDataBinding")
  val VIEW = getObjectTypeByInternalName("android/view/View")
}
