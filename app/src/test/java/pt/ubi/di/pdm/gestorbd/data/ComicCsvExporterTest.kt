package pt.ubi.di.pdm.gestorbd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComicCsvExporterTest {

    @Test
    fun exportsHeaderAndEscapesCommas() {
        val csv = ComicCsvExporter.toCsv(
            listOf(
                Comic(
                    folderId = 1,
                    title = "A, B",
                    publisher = "Casterman",
                    issueNumber = "1",
                    grade = 9.5,
                    acquisitionDate = LocalDate.of(2025, 1, 2),
                    certification = "CGC 9.8",
                    notes = "nota \"rara\"",
                ),
            ),
        )
        val lines = csv.lines().filter { it.isNotEmpty() }
        assertEquals(
            "title,publisher,issue,grade,acquisition_date,certification,notes",
            lines[0],
        )
        assertTrue(lines[1].startsWith("\"A, B\",Casterman,1,"))
        assertTrue(lines[1].contains("CGC 9.8"))
        assertTrue(lines[1].contains("\"nota \"\"rara\"\"\""))
    }
}
