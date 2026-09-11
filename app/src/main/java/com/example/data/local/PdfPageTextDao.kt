package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PdfPageTextEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfPageTextDao {

    @Query("SELECT text FROM pdf_page_text WHERE bookId = :bookId AND pageIndex = :pageIndex")
    suspend fun getPageText(bookId: Long, pageIndex: Int): String?

    @Query("SELECT COUNT(*) FROM pdf_page_text WHERE bookId = :bookId")
    suspend fun countIndexedPages(bookId: Long): Int

    @Query("SELECT COUNT(*) FROM pdf_page_text WHERE bookId = :bookId")
    fun observeIndexedPageCount(bookId: Long): Flow<Int>

    @Query("SELECT pageIndex FROM pdf_page_text WHERE bookId = :bookId")
    suspend fun getIndexedPageIndices(bookId: Long): List<Int>

    @Query("SELECT COUNT(*) FROM pdf_page_text WHERE bookId = :bookId AND text != ''")
    suspend fun countPagesWithText(bookId: Long): Int

    @Query(
        """
        SELECT * FROM pdf_page_text
        WHERE bookId = :bookId AND text LIKE '%' || :query || '%'
        ORDER BY pageIndex ASC
        LIMIT :limit
        """
    )
    suspend fun searchPages(bookId: Long, query: String, limit: Int): List<PdfPageTextEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(pages: List<PdfPageTextEntity>)

    @Query("DELETE FROM pdf_page_text WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}
