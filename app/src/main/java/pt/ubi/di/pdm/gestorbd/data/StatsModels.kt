package pt.ubi.di.pdm.gestorbd.data

/** Modelos POJO mapeados pelo Room a partir de SELECT agregados (stats). */
data class CollectionSummary(val total: Int, val averageGrade: Double?)

data class PublisherCount(val publisher: String, val total: Int)

/** [bucket] = parte inteira do grau (9 agrupa 9.0 e 9.5). */
data class GradeBucket(val bucket: Int, val total: Int)
