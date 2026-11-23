package pt.ubi.di.pdm.gestorbd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Exemplar de banda desenhada pertencente a uma [BdFolder].
 *
 * [certification] é opcional (ex.: «CGC 9.8», «CBCS»): texto livre para
 * o colecionador focado em valor, sem enum rígido.
 */
@Entity(
    tableName = "comics",
    foreignKeys = [
        ForeignKey(
            entity = BdFolder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["publisher"]),
        Index(value = ["title"]),
    ],
)
data class Comic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "folder_id") val folderId: Long,
    val title: String,
    val publisher: String,
    @ColumnInfo(name = "issue_number") val issueNumber: String,
    /** Grau de conservação CGC-like, de 1.0 a 10.0 (passos de 0.5 na UI). */
    val grade: Double,
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: LocalDate,
    val notes: String = "",
    /** Certificação / grading company (opcional). */
    val certification: String = "",
    @ColumnInfo(name = "cover_file_name") val coverFileName: String? = null,
)
