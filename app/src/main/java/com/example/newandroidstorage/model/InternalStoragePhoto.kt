package com.example.newandroidstorage.model

import android.graphics.Bitmap

data class InternalStoragePhoto(
    val name: String,
    val bmp: Bitmap,
    val absolutePath : String,
    val path : String
)
