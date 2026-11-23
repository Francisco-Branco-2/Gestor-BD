package pt.ubi.di.pdm.gestorbd.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query(
        """
        SELECT f.*, COUNT(c.id) AS comic_count
        FROM bd_folders f
        LEFT JOIN comics c ON c.folder_id = f.id
        GROUP BY f.id
        ORDER BY f.name COLLATE NOCASE
        """
    )
    fun observeAllWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM bd_folders WHERE id = :id")
    fun observeById(id: Long): Flow<BdFolder?>

    @Query("SELECT * FROM bd_folders WHERE id = :id")
    suspend fun getById(id: Long): BdFolder?

    @Insert
    suspend fun insert(folder: BdFolder): Long

    @Update
    suspend fun update(folder: BdFolder): Int

    @Delete
    suspend fun delete(folder: BdFolder): Int
}
