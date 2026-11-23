package pt.ubi.di.pdm.gestorbd.data

/**
 * Intervalos de grau usados no chip de filtro.
 * [maxGradeExclusive] é exclusivo para os intervalos não se sobreporem.
 */
enum class GradeFilter(val minGrade: Double?, val maxGradeExclusive: Double?) {
    ALL(null, null),
    HIGH(8.0, null),
    MEDIUM(5.0, 8.0),
    LOW(null, 5.0),
}

/**
 * Critério de ordenação da lista.
 * Aplicado em memória após a query SQL — mantém uma única consulta Room.
 */
enum class SortOrder {
    TITLE,
    GRADE_DESC,
    DATE_DESC,
}

/**
 * Estado dos filtros da lista de exemplares.
 * [folderId] delimita sempre a pasta aberta (relação 1:N).
 */
data class ComicFilters(
    val folderId: Long,
    val query: String = "",
    val publisher: String? = null,
    val grade: GradeFilter = GradeFilter.ALL,
    val sort: SortOrder = SortOrder.TITLE,
) {
    /** true se pesquisa ou filtros estão ativos (a ordenação sozinha não conta). */
    val isFiltering: Boolean
        get() = query.isNotBlank() || publisher != null || grade != GradeFilter.ALL
}
