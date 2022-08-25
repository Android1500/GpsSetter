package com.android1500.gpssetter.repository


import androidx.annotation.WorkerThread
import com.android1500.gpssetter.room.Favourite
import com.android1500.gpssetter.room.UserDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepo @Inject constructor(private val userDao: UserDao) {

    val getAllFavourites: Flow<List<Favourite>> get() =  userDao.getAllFavourites()

        @Suppress("RedundantSuspendModifier")
        @WorkerThread
        suspend fun addNewFavourite(favourite: Favourite) : Long {
            return userDao.insertToRoomDatabase(favourite)
        }

        suspend fun deleteFavourite(favourite: Favourite) {
          userDao.deleteSingleFavourite(favourite)
       }


       fun getSingleFavourite(id: Long) : Favourite {
       return userDao.getSingleFavourite(id)

    }



}