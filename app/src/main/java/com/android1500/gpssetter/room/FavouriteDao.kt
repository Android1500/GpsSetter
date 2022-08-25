package com.android1500.gpssetter.room
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavouriteDao {

        //insert data to room database
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertToRoomDatabase(favourite: Favourite) : Long

        // for update single favourite
        @Update
        suspend fun updateUserDetails(favourite: Favourite)

        //delete single favourite
        @Delete
        suspend fun deleteSingleFavourite(favourite: Favourite)

       //get all Favourite inserted to room database...normally this is supposed to be a list of Favourites
        @Transaction
        @Query("SELECT * FROM favourite ORDER BY id DESC")
        fun getAllFavourites() : Flow<List<Favourite>>

        //get single favourite inserted to room database
        @Transaction
        @Query("SELECT * FROM favourite WHERE id = :id ORDER BY id DESC")
        fun getSingleFavourite(id: Long) : Favourite




}