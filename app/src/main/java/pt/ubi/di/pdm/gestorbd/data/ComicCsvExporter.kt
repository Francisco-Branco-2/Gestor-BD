package pt.ubi.di.pdm.gestorbd.data

import pt.ubi.di.pdm.gestorbd.ui.common.Formatters

/**
 * Gera CSV simples (UTF-8) da lista de exemplares para partilha/exportação.
 * Aspas e vírgulas nos campos são escapadas segundo a convenção CSV básica.
 */
object ComicCsvExporter {

    private val header = listOf(
        "title", "publisher", "issue", "grade", "acquisition_date", "certification", "notes",
    )

    fun toCsv(comics: List<Comic>): String = buildString {
        appendLine(header.joinToString(",") { escape(it) })
        comics.forEach { comic ->
            appendLine(
                listOf(
                    comic.title,
                    comic.publisher,
                    comic.issueNumber,
                    Formatters.grade(comic.grade),
                    comic.acquisitionDate.toString(),
                    comic.certification,
                    comic.notes,
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun escape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
