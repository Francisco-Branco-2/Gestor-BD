package pt.ubi.di.pdm.gestorbd.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formatação de datas/graus partilhada entre lista, detalhe, edição e stats. */
object Formatters {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun date(date: LocalDate): String = date.format(dateFormatter)

    /** Locale.US força ponto decimal (padrão CGC: "9.5"), independente do idioma do telemóvel. */
    fun grade(value: Double): String = String.format(Locale.US, "%.1f", value)
}
