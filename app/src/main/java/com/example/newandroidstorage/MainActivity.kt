package com.example.newandroidstorage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.newandroidstorage.adapter.InternalStoragePhotoAdapter
import com.example.newandroidstorage.databinding.ActivityMainBinding
import com.example.newandroidstorage.model.InternalStoragePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMainBinding
    private lateinit var internalStoragePhotoAdapter: InternalStoragePhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = InternalStoragePhotoAdapter {
            val isDeleted = deletePhotoFromInternalStorage(it.name)
            if(isDeleted){
                loadPhotosFromInternalStorageIntoRecyclerView()
                Toast.makeText(this, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this, "Failed to delete photo", Toast.LENGTH_SHORT).show()
            }
        }

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()){
            it?.let {
                val isPrivate = binding.switchPrivate.isChecked
                if(isPrivate){
                    val timeStamps = System.currentTimeMillis()
                    val isSaved = savePhotoToInternalStorage("image_$timeStamps", it)
                    if(isSaved){
                        loadPhotosFromInternalStorageIntoRecyclerView()
                        Toast.makeText(this, "Photo saved successfully", Toast.LENGTH_SHORT).show()
                    }else{
                        Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setupInternalStorageRecyclerView()
        loadPhotosFromInternalStorageIntoRecyclerView()

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }
    }

    private fun setupInternalStorageRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalStoragePhotoAdapter
        layoutManager = StaggeredGridLayoutManager(4, RecyclerView.VERTICAL)
    }
    private fun loadPhotosFromInternalStorageIntoRecyclerView(){
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalStoragePhotoAdapter.submitList(photos)
        }
    }

    private fun savePhotoToInternalStorage(fileName : String,bmp : Bitmap) :  Boolean{
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
                if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)){
                    throw IOException("Couldn't save image.")
                }
            }

            return true
        }catch (e : IOException){
            e.printStackTrace()
            false
        }
    }

    private fun deletePhotoFromInternalStorage(fileName: String) : Boolean {
        return try {
            deleteFile(fileName)
        }catch (e : IOException){
            e.printStackTrace()
            false
        }
    }

    private suspend fun loadPhotosFromInternalStorage() : List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO){
            // created a directory to read images from, no need if directly reading from internal storage
//            val directory = File(filesDir, "image")
//            val files = directory.listFiles()

            val files = filesDir.listFiles()
            files?.filter {it.canRead() && it.isFile && it.name.endsWith(".jpg") }?.map {
                val bytes = it.readBytes()
                val absolutePath = it.absolutePath
                val path = it.path
                val bmp = BitmapFactory.decodeByteArray(bytes,0,bytes.size)
                InternalStoragePhoto(name = it.name,bmp = bmp, absolutePath = absolutePath, path = path)
            } ?: listOf()
        }
    }
}