package com.example.newandroidstorage

import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.newandroidstorage.adapter.InternalStoragePhotoAdapter
import com.example.newandroidstorage.adapter.SharedPhotoAdapter
import com.example.newandroidstorage.databinding.ActivityMainBinding
import com.example.newandroidstorage.model.InternalStoragePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter
    private lateinit var sharedPhotoAdapter: SharedPhotoAdapter
    private var readPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeleted = deletePhotoFromInternalStorage(it.name)
            if (isDeleted) {
                loadPhotosFromInternalStorageIntoRecyclerView()
                Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
        }

        sharedPhotoAdapter = SharedPhotoAdapter {

        }

        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
            readPermissionGranted = it[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted = it[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

        }

        updateOrRequestPermissions()


        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            it?.let {
                val isPrivate = binding.switchPrivate.isChecked
                val timeStamps = System.currentTimeMillis()
                val isSaved = when {
                    isPrivate -> savePhotoToInternalStorage("image_$timeStamps", it)
                    writePermissionGranted ->  savePhotosToExternalStorage("image_$timeStamps",it)
                    else -> false
                }

                if(isPrivate){
                    loadPhotosFromInternalStorageIntoRecyclerView()
                }
                if (isSaved) {
                    Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setupInternalStorageRecyclerView()
        loadPhotosFromInternalStorageIntoRecyclerView()

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }
    }

    private fun updateOrRequestPermissions() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED


        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if (!writePermissionGranted) permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!readPermissionGranted) permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun savePhotosToExternalStorage(displayName: String, bmp: Bitmap): Boolean {
        val imageCollection = sdk29AndUp {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValue = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.WIDTH, bmp.width)
            put(MediaStore.Images.Media.HEIGHT, bmp.height)

            // relative path will save in specific folder
//            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/imageToSave")
        }

        return try {
            contentResolver.insert(imageCollection, contentValue)?.also { uri ->
                contentResolver.openOutputStream(uri).use { stream ->
                    stream?.let {
                        if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                            throw IOException("couldn't save image")
                        }
                    }
                }
            } ?: throw IOException("Failed to create mediastore entry ")
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }

    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(4, RecyclerView.VERTICAL)
    }

    private fun loadPhotosFromInternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)
        }
    }

    private fun savePhotoToInternalStorage(fileName: String, bmp: Bitmap): Boolean {
        return try {
            // this will crate a image directory inside app internal storage and store the image inside that directory
//            val directory = File(filesDir, "image")
//            if (!directory.exists()) {
//                directory.mkdirs()
//            }
//
//            val filePath = File(directory, "$fileName.jpg")
//
//            filePath.outputStream().use {stream ->
//                if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)){
//                    throw IOException("Couldn't save image.")
//                }
//            }

            // below code will save to app internal storage directly
            openFileOutput("$fileName.jpg", MODE_PRIVATE).use { stream ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                    throw IOException("Couldn't save image.")
                }
            }

            return true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun deletePhotoFromInternalStorage(fileName: String): Boolean {
        return try {
            deleteFile(fileName)
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotosFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            // created a directory to read images from, no need if directly reading from internal storage
//            val directory = File(filesDir, "image")
//            val files = directory.listFiles()

            val files = filesDir.listFiles()
            files?.filter { it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val absolutePath = it.absolutePath
                val path = it.path
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                InternalStoragePhoto(
                    name = it.name,
                    bmp = bmp,
                    absolutePath = absolutePath,
                    path = path
                )
            } ?: listOf()
        }
    }
}