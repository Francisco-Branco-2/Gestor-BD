package pt.ubi.di.pdm.gestorbd.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Única porta de acesso aos dados para a UI (padrão Repository).
 * Coordena pastas, exemplares e ficheiros de capa num só sítio.
 */
class ComicRepository(
    private val folderDao: FolderDao,
    private val comicDao: ComicDao,
    private val coverStorage: CoverImageStorage,
) {

    // --- Pastas ---

    fun observeFolders(): Flow<List<FolderWithCount>> = folderDao.observeAllWithCount()

    fun observeFolder(id: Long): Flow<BdFolder?> = folderDao.observeById(id)

    suspend fun getFolder(id: Long): BdFolder? = folderDao.getById(id)

    suspend fun saveFolder(folder: BdFolder, previousCoverFileName: String?) {
        if (folder.id == 0L) folderDao.insert(folder) else folderDao.update(folder)
        if (previousCoverFileName != null && previousCoverFileName != folder.coverFileName) {
            coverStorage.delete(previousCoverFileName)
        }
    }

    suspend fun deleteFolder(folder: BdFolder) {
        val comicCovers = comicDao.getByFolder(folder.id).mapNotNull { it.coverFileName }
        folderDao.delete(folder)
        coverStorage.delete(folder.coverFileName)
        comicCovers.forEach { coverStorage.delete(it) }
    }

    // --- Exemplares ---

    /** Pesquisa SQL + ordenação em memória segundo [ComicFilters.sort]. */
    fun search(filters: ComicFilters): Flow<List<Comic>> = comicDao.search(
        folderId = filters.folderId,
        query = filters.query.trim(),
        publisher = filters.publisher,
        minGrade = filters.grade.minGrade,
        maxGrade = filters.grade.maxGradeExclusive,
    ).map { list -> sortComics(list, filters.sort) }

    suspend fun listForExport(folderId: Long): List<Comic> =
        sortComics(comicDao.getByFolder(folderId), SortOrder.TITLE)

    fun observe(id: Long): Flow<Comic?> = comicDao.observeById(id)

    suspend fun get(id: Long): Comic? = comicDao.getById(id)

    fun observePublishers(folderId: Long): Flow<List<String>> = comicDao.observePublishers(folderId)

    suspend fun save(comic: Comic, previousCoverFileName: String?) {
        if (comic.id == 0L) comicDao.insert(comic) else comicDao.update(comic)
        if (previousCoverFileName != null && previousCoverFileName != comic.coverFileName) {
            coverStorage.delete(previousCoverFileName)
        }
    }

    suspend fun delete(comic: Comic) {
        comicDao.delete(comic)
        coverStorage.delete(comic.coverFileName)
    }

    fun observeSummary(folderId: Long): Flow<CollectionSummary> = comicDao.observeSummary(folderId)

    fun observePublisherCounts(folderId: Long): Flow<List<PublisherCount>> =
        comicDao.observePublisherCounts(folderId)

    fun observeGradeDistribution(folderId: Long): Flow<List<GradeBucket>> =
        comicDao.observeGradeDistribution(folderId)

    private fun sortComics(list: List<Comic>, sort: SortOrder): List<Comic> = when (sort) {
        SortOrder.TITLE -> list.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { comic: Comic -> comic.title }
                .thenBy { it.issueNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.issueNumber },
        )
        SortOrder.GRADE_DESC -> list.sortedWith(
            compareByDescending<Comic> { it.grade }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        SortOrder.DATE_DESC -> list.sortedWith(
            compareByDescending<Comic> { it.acquisitionDate }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
    }
}
