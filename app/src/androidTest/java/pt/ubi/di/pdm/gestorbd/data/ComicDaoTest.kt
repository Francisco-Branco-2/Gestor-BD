package pt.ubi.di.pdm.gestorbd.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Testa pastas + comics numa BD em memória (inclui CASCADE ao apagar pasta). */
@RunWith(AndroidJUnit4::class)
class ComicDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var folderDao: FolderDao
    private lateinit var comicDao: ComicDao
    private var folderId: Long = 0

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderDao = database.folderDao()
        comicDao = database.comicDao()
        folderId = folderDao.insert(BdFolder(name = "Tintin", authors = "Hergé"))
    }

    @After
    fun tearDown() = database.close()

    private fun comic(title: String, publisher: String, grade: Double, folder: Long = folderId) =
        Comic(
            folderId = folder,
            title = title,
            publisher = publisher,
            issueNumber = "1",
            grade = grade,
            acquisitionDate = LocalDate.of(2025, 1, 1),
        )

    @Test
    fun insertUpdateAndDeleteComic() = runBlocking {
        val id = comicDao.insert(comic("Spirou", "Dupuis", 8.0))
        comicDao.update(comicDao.getById(id)!!.copy(title = "Spirou e Fantásio"))
        assertEquals("Spirou e Fantásio", comicDao.getById(id)!!.title)
        comicDao.delete(comicDao.getById(id)!!)
        assertNull(comicDao.getById(id))
    }

    @Test
    fun searchIsScopedToFolder() = runBlocking {
        val other = folderDao.insert(BdFolder(name = "Astérix", authors = "Goscinny"))
        comicDao.insert(comic("Tintin", "Casterman", 9.5))
        comicDao.insert(comic("Astérix", "Dargaud", 6.0, folder = other))

        assertEquals(1, comicDao.search(folderId, "", null, null, null).first().size)
        assertEquals("Tintin", comicDao.search(folderId, "tin", null, null, null).first().single().title)
    }

    @Test
    fun searchFindsCertificationAndPersistsIt() = runBlocking {
        val id = comicDao.insert(
            comic("Tintin", "Casterman", 9.8).copy(certification = "CGC 9.8"),
        )
        assertEquals("CGC 9.8", comicDao.getById(id)!!.certification)
        assertEquals("Tintin", comicDao.search(folderId, "CGC", null, null, null).first().single().title)
    }

    @Test
    fun deletingFolderCascadesComics() = runBlocking {
        comicDao.insert(comic("A", "Casterman", 9.0))
        comicDao.insert(comic("B", "Casterman", 8.0))
        folderDao.delete(folderDao.getById(folderId)!!)
        assertTrue(comicDao.getByFolder(folderId).isEmpty())
        assertNull(folderDao.getById(folderId))
    }

    @Test
    fun folderListShowsComicCount() = runBlocking {
        comicDao.insert(comic("A", "Casterman", 9.0))
        comicDao.insert(comic("B", "Casterman", 8.0))
        val item = folderDao.observeAllWithCount().first().single()
        assertEquals(2, item.comicCount)
        assertEquals("Tintin", item.folder.name)
    }

    @Test
    fun statisticsAggregateTheFolder() = runBlocking {
        comicDao.insert(comic("A", "Marvel", 9.5))
        comicDao.insert(comic("B", "Marvel", 9.0))
        comicDao.insert(comic("C", "DC", 4.0))

        val summary = comicDao.observeSummary(folderId).first()
        assertEquals(3, summary.total)
        assertEquals(7.5, summary.averageGrade!!, 0.001)
        assertEquals(PublisherCount("Marvel", 2), comicDao.observePublisherCounts(folderId).first().first())
        assertEquals(listOf(GradeBucket(4, 1), GradeBucket(9, 2)), comicDao.observeGradeDistribution(folderId).first())
    }
}
