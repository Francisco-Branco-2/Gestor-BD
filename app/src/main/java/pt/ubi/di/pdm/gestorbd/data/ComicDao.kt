package pt.ubi.di.pdm.gestorbd.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Acesso SQL à tabela comics.
 * A ordenação final é feita no Repository ([SortOrder]), para evitar
 * três queries quase iguais só por causa do ORDER BY.
 */
@Dao
interface ComicDao {

    @Query(
        """
        SELECT * FROM comics
        WHERE folder_id = :folderId
          AND (:query = '' OR title LIKE '%' || :query || '%'
               OR issue_number LIKE '%' || :query || '%'
               OR notes LIKE '%' || :query || '%'
               OR certification LIKE '%' || :query || '%')
          AND (:publisher IS NULL OR publisher = :publisher COLLATE NOCASE)
          AND (:minGrade IS NULL OR grade >= :minGrade)
          AND (:maxGrade IS NULL OR grade < :maxGrade)
        """
    )
    fun search(
        folderId: Long,
        query: String,
        publisher: String?,
        minGrade: Double?,
        maxGrade: Double?,
    ): Flow<List<Comic>>

    @Query("SELECT * FROM comics WHERE id = :id")
    fun observeById(id: Long): Flow<Comic?>

    @Query("SELECT * FROM comics WHERE id = :id")
    suspend fun getById(id: Long): Comic?

    @Query("SELECT * FROM comics WHERE folder_id = :folderId")
    suspend fun getByFolder(folderId: Long): List<Comic>

    @Query(
        """
        SELECT publisher FROM comics
        WHERE folder_id = :folderId
        GROUP BY publisher COLLATE NOCASE
        ORDER BY publisher COLLATE NOCASE
        """
    )
    fun observePublishers(folderId: Long): Flow<List<String>>

    @Insert
    suspend fun insert(comic: Comic): Long

    @Update
    suspend fun update(comic: Comic): Int

    @Delete
    suspend fun delete(comic: Comic): Int

    @Query("SELECT COUNT(*) AS total, AVG(grade) AS averageGrade FROM comics WHERE folder_id = :folderId")
    fun observeSummary(folderId: Long): Flow<CollectionSummary>

    @Query(
        """
        SELECT publisher, COUNT(*) AS total FROM comics
        WHERE folder_id = :folderId
        GROUP BY publisher COLLATE NOCASE
        ORDER BY total DESC, publisher COLLATE NOCASE
        """
    )
    fun observePublisherCounts(folderId: Long): Flow<List<PublisherCount>>

    @Query(
        """
        SELECT CAST(grade AS INTEGER) AS bucket, COUNT(*) AS total FROM comics
        WHERE folder_id = :folderId
        GROUP BY bucket ORDER BY bucket
        """
    )
    fun observeGradeDistribution(folderId: Long): Flow<List<GradeBucket>>
}
