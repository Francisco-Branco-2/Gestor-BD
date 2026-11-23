package pt.ubi.di.pdm.gestorbd.data

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * SQLite não tem tipo DATE nativo.
 * Guardar [LocalDate] como epoch-day evita ambiguidades de fuso horário
 * que surgem quando se usa milissegundos com [java.util.Date].
 */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
