package com.android1500.gpssetter.repository


import androidx.annotation.WorkerThread
import com.android1500.gpssetter.room.User
import com.android1500.gpssetter.room.UserDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class UserRepo @Inject constructor(private val userDao: UserDao) {

    val getUserDetails: Flow<List<User>> get() =  userDao.getUserDetails()

        @Suppress("RedundantSuspendModifier")
        @WorkerThread
        suspend fun createUserRecords(user: User) : Long {
            return userDao.insertToRoomDatabase(user)
        }

        suspend fun deleteUserRecord(user: User) {
          userDao.deleteAllUsersDetails(user)
       }


       fun getSingleUser(id: Long) : User {
       return userDao.getSingleUserDetails(id)

    }



}