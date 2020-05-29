[![Build Status](https://travis-ci.org/MichaelRocks/DataBindingCompat.svg?branch=master)](https://travis-ci.org/MichaelRocks/DataBindingCompat)

Deprecated
==========

`VectorDrawable`s are supported in DataBinding natively since [AGP 4.0.0](https://issuetracker.google.com/issues/123427765).

DataBindingCompat
=================

A Gradle plugin that adds support for
[`VectorDrawableCompat`](https://developer.android.com/reference/android/support/graphics/drawable/VectorDrawableCompat.html)
to the [Data Binding Library](https://developer.android.com/topic/libraries/data-binding/index.html).

Why?
----

The Data Binding Library supports
[resources in binding expressions](https://developer.android.com/topic/libraries/data-binding/index.html#resources),
and drawable resources in particular.

```xml
  <ImageView
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:src="@{condition ? @drawable/image1 : @drawable/image2}" />
```

But if your project uses support vector drawables you're in a big trouble.

```groovy
android {  
  defaultConfig {  
    vectorDrawables.useSupportLibrary = true  
  }  
} 
```

Unfortunately, the Data Binding Library doesn't call `AppCompatResources.getDrawable()` when loading drawables, so the
binding above will throw an exception at runtime on pre-Lollipop devices. DataBindingCompat solves this issue and all
you need to do is just to apply the Gradle plugin.

Usage
-----

```groovy
buildscript {
  repositories {
    jcenter()
  }

  dependencies {
    classpath 'io.michaelrocks:databindingcompat:1.1.7'
  }
}

apply plugin: 'com.android.application'
apply plugin: 'io.michaelrocks.databindingcompat'
```

How it works
------------

When you use a drawable resource in a binding expression the Data Binding Library inflates this drawable by calling
the `ViewDataBinding.getDrawableFromResource()` method. The DataBindingCompat plugin patches `ViewDataBinding` class at
compile time and replaces the implementation of `getDrawableFromResource()` with an invocation of
`AppCompatResources.getDrawable()`. The same transformation the plugin does with the
`ViewDataBinding.getColorStateListFromResource()` method. And that's it.

`ViewDataBinding` *before* patching: 
```java
protected static Drawable getDrawableFromResource(View view, int resourceId) {
  if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
    return view.getContext().getDrawable(resourceId);
  } else {
    return view.getResources().getDrawable(resourceId);
  }
}

protected static ColorStateList getColorStateListFromResource(View view, int resourceId) {
  if (VERSION.SDK_INT >= VERSION_CODES.M) {
    return view.getContext().getColorStateList(resourceId);
  } else {
    return view.getResources().getColorStateList(resourceId);
  }
}
```

`ViewDataBinding` *after* patching: 
```java
protected static Drawable getDrawableFromResource(View view, int resourceId) {
  return AppCompatResources.getDrawable(view.getContext(), resourceId));
}

protected static ColorStateList getColorStateListFromResource(View view, int resourceId) {
  return AppCompatResources.getColorStateList(view.getContext(), resourceId));
}
```

License
-------

    Copyright 2017 Michael Rozumyanskiy

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
