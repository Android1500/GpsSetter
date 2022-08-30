package com.android1500.gpssetter.viewmodel


import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.R
import com.android1500.gpssetter.repository.FavouriteRepository
import com.android1500.gpssetter.repository.SettingsRepository
import com.android1500.gpssetter.room.Favourite
import com.android1500.gpssetter.selfhook.XposedSelfHooks
import com.android1500.gpssetter.update.UpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt


@HiltViewModel
class MainViewModel @Inject constructor(
    private val favouriteRepository: FavouriteRepository,
    private val settingsRepo: SettingsRepository,
    private val updateChecker: UpdateChecker,
    private val downloadManager: DownloadManager,
    @ApplicationContext context: Context
) : ViewModel() {





    val getLat  = settingsRepo.getLat
    val getLng  = settingsRepo.getLng
    val isStarted = settingsRepo.isStarted



    
   private val _allFavList = MutableStateFlow<List<Favourite>>(emptyList())
    val allFavList : StateFlow<List<Favourite>> =  _allFavList
    fun doGetUserDetails(){
        viewModelScope.launch(Dispatchers.IO) {
            favouriteRepository.getAllFavourites
                .catch { e ->
                    Timber.tag("Error getting all save favourite").d(e.message.toString())
                }
                .collect {
                    _allFavList.emit(it)
                }
        }
    }


    fun update(start: Boolean, la: Double, ln: Double) = viewModelScope.launch {
        settingsRepo.update(start,la,ln)
    }

    private val _response = MutableLiveData<Long>()
    val response: LiveData<Long> = _response


    fun insertNewFavourite(favourite: Favourite) = viewModelScope.launch(Dispatchers.IO){
        _response.postValue(favouriteRepository.addNewFavourite(favourite))

    }


    val isXposed = MutableLiveData<Boolean>()
    fun updateXposedState() {
        viewModelScope.launch {
            isXposed.value = XposedSelfHooks.isXposedModuleEnabled()
        }
    }


    fun deleteFavourite(favourite: Favourite) = viewModelScope.launch {
        favouriteRepository.deleteFavourite(favourite)
    }

    fun getFavouriteSingle(i : Int) : Favourite{
        return favouriteRepository.getSingleFavourite(i.toLong())
    }


    private val _update = MutableStateFlow<UpdateChecker.Update?>(null).apply {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                updateChecker.clearCachedDownloads(context)
            }
            updateChecker.getLatestRelease().collect {
                emit(it)
            }
        }
    }

     val update = _update.asStateFlow()
    fun getAvailableUpdate(): UpdateChecker.Update? {
        return _update.value
    }

    fun clearUpdate() {
        viewModelScope.launch {
            _update.emit(null)
        }
    }


    private var requestId: Long? = null
    private var _downloadState = MutableStateFlow<State>(State.Idle)
    private var downloadFile: File? = null
    val downloadState = _downloadState.asStateFlow()


    // Got idea from https://github.com/KieronQuinn/DarQ for Check Update

    fun startDownload(context: Context, update: UpdateChecker.Update) {
        if(_downloadState.value is State.Idle) {
            downloadUpdate(context, update.assetUrl, update.assetName)
        }
    }

    private val downloadStateReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            viewModelScope.launch {
                var success = false
                val query = DownloadManager.Query().apply {
                    setFilterById(requestId ?: return@apply)
                }
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (cursor.getInt(columnIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                        success = true
                    }
                }
                if (success && downloadFile != null) {
                    val outputUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", downloadFile!!)
                    _downloadState.emit(State.Done(outputUri))
                } else {
                    _downloadState.emit(State.Failed)
                }
            }
        }
    }

    private val downloadObserver = object: ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            viewModelScope.launch {
                val query = DownloadManager.Query()
                query.setFilterById(requestId ?: return@launch)
                val c: Cursor = downloadManager.query(query)
                var progress = 0.0
                if (c.moveToFirst()) {
                    val sizeIndex: Int =
                        c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val downloadedIndex: Int =
                        c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val size = c.getInt(sizeIndex)
                    val downloaded = c.getInt(downloadedIndex)
                    if (size != -1) progress = downloaded * 100.0 / size
                }
                _downloadState.emit(State.Downloading(progress.roundToInt()))
            }
        }
    }

    private fun downloadUpdate(context: Context, url: String, fileName: String) = viewModelScope.launch {
        val downloadFolder = File(context.externalCacheDir, "updates").apply {
            mkdirs()
        }
        downloadFile = File(downloadFolder, fileName)
        context.registerReceiver(downloadStateReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        context.contentResolver.registerContentObserver(Uri.parse("content://downloads/my_downloads"), true, downloadObserver)
        requestId = DownloadManager.Request(Uri.parse(url)).apply {
            setDescription(context.getString(R.string.download_manager_description))
            setTitle(context.getString(R.string.app_name))
            setDestinationUri(Uri.fromFile(downloadFile!!))
        }.run {
            downloadManager.enqueue(this)
        }
    }



    fun openPackageInstaller(context: Context, uri: Uri){
        Intent(Intent.ACTION_VIEW, uri).apply {
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.also {
            context.startActivity(it)
        }
    }

    fun cancelDownload(context: Context) {
        viewModelScope.launch {
            requestId?.let {
                downloadManager.remove(it)
            }
            context.unregisterReceiver(downloadStateReceiver)
            context.contentResolver.unregisterContentObserver(downloadObserver)
            _downloadState.emit(State.Idle)
        }
    }











    sealed class State {
        object Idle: State()
        data class Downloading(val progress: Int): State()
        data class Done(val fileUri: Uri): State()
        object Failed: State()
    }





}