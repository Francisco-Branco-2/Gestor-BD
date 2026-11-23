package pt.ubi.di.pdm.gestorbd.data

import androidx.room.ColumnInfo
import androidx.room.Embedded

/** Resultado do JOIN pasta + COUNT(exemplares) para a lista principal. */
data class FolderWithCount(
    @Embedded val folder: BdFolder,
    @ColumnInfo(name = "comic_count") val comicCount: Int,
)
