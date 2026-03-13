package com.github.garynasser.correction_notebook

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp  // 👈 这个注解必须在这里，且只能在这里！
class MyApplication : Application()