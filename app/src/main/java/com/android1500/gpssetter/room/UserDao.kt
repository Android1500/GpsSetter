package com.android1500.gpssetter.room
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

        //insert data to room database
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertToRoomDatabase(user: User) : Long

        @Update
        suspend fun updateUserDetails(user: User)

        //delete single user details
        @Query("DELETE FROM user WHERE id = :id")
        suspend fun deleteSingleUserDetails(id: Int)

        //delete all user details
        @Delete
        suspend fun deleteAllUsersDetails(user: User)

    //get all users inserted to room database...normally this is supposed to be a list of users
        @Transaction
        @Query("SELECT * FROM user ORDER BY id DESC")
        fun getUserDetails() : Flow<List<User>>

    //get single user inserted to room database
        @Transaction
        @Query("SELECT * FROM user WHERE id = :id ORDER BY id DESC")
        fun getSingleUserDetails(id: Long) : User




}