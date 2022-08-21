package com.android1500.gpssetter.viewmodel


import androidx.lifecycle.*
import com.android1500.gpssetter.repository.SettingsRepository
import com.android1500.gpssetter.selfhook.XposedSelfHooks
import com.android1500.gpssetter.room.User
import com.android1500.gpssetter.repository.UserRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(
    private val userRepo: UserRepo,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {



    val getLat  = settingsRepo.getLat
    val getLng  = settingsRepo.getLng
    val isStarted = settingsRepo.isStarted
    
   private val _allFavList = MutableStateFlow<List<User>>(emptyList())
    val allFavList : StateFlow<List<User>> =  _allFavList
    fun doGetUserDetails(){
        viewModelScope.launch(Dispatchers.IO) {
            userRepo.getUserDetails
                .catch { e ->
                    Timber.tag("Error").d(e.message.toString())
                }
                .collect {
                    _allFavList.value = it
                }
        }
    }


    fun update(start: Boolean, la: Double, ln: Double) = viewModelScope.launch {
        settingsRepo.update(start,la,ln)
    }

    private val _response = MutableLiveData<Long>()
    val response: LiveData<Long> = _response


    fun insertUserDetails(user: User){
        viewModelScope.launch(Dispatchers.IO) {
            _response.postValue(userRepo.createUserRecords(user))
        }
    }


    val isXposed = MutableLiveData<Boolean>()
    fun updateXposedState() {
        viewModelScope.launch {
            isXposed.value = XposedSelfHooks.isXposedModuleEnabled()
        }
    }


    fun deleteFavourite(user: User) = viewModelScope.launch {
        userRepo.deleteUserRecord(user)
    }

    fun getFavouriteSingle(i : Int) : User{
        return userRepo.getSingleUser(i.toLong())
    }



}