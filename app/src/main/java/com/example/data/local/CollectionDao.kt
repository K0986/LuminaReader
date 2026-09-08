package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.BookCollectionCrossRef
import com.example.data.model.Collection
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query("SELECT * FROM collections ORDER BY createdAt ASC")
    fun getAllCollections(): Flow<List<Collection>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: Collection): Long

    @Delete
    suspend fun deleteCollection(collection: Collection)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addBookToCollection(crossRef: BookCollectionCrossRef)

    @Query("DELETE FROM book_collection_cross_ref WHERE bookId = :bookId AND collectionId = :collectionId")
    suspend fun removeBookFromCollection(bookId: Long, collectionId: Long)

    @Query("SELECT collectionId FROM book_collection_cross_ref WHERE bookId = :bookId")
    fun getCollectionIdsForBook(bookId: Long): Flow<List<Long>>
}
