package pt.ubi.di.pdm.gestorbd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Pasta/coleção que agrupa exemplares (ex.: «Tintin», «Astérix»).
 *
 * Modelo 1:N simples: uma pasta tem muitos [Comic].
 * A capa segue a mesma regra dos exemplares — só o nome do ficheiro na BD.
 */
@Entity(tableName = "bd_folders")
data class BdFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val authors: String,
    @ColumnInfo(name = "cover_file_name") val coverFileName: String? = null,
)
