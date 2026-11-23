package pt.ubi.di.pdm.gestorbd.domain

import java.time.LocalDate

/**
 * Regras de validação do formulário, isoladas da framework Android.
 * [certification] é opcional: só se valida o comprimento máximo.
 */
object ComicValidator {

    enum class Field { TITLE, PUBLISHER, ISSUE, ACQUISITION_DATE, GRADE, NOTES, CERTIFICATION }

    enum class Error { REQUIRED, TOO_LONG, FUTURE_DATE, OUT_OF_RANGE }

    data class Input(
        val title: String,
        val publisher: String,
        val issueNumber: String,
        val grade: Double,
        val acquisitionDate: LocalDate?,
        val notes: String,
        val certification: String = "",
    )

    const val MIN_GRADE = 1.0
    const val MAX_GRADE = 10.0

    fun maxLength(field: Field): Int = when (field) {
        Field.TITLE -> 120
        Field.PUBLISHER -> 60
        Field.ISSUE -> 20
        Field.NOTES -> 1000
        Field.CERTIFICATION -> 40
        else -> Int.MAX_VALUE
    }

    fun validate(input: Input, today: LocalDate): Map<Field, Error> {
        val errors = linkedMapOf<Field, Error>()
        checkText(Field.TITLE, input.title, required = true)?.let { errors[Field.TITLE] = it }
        checkText(Field.PUBLISHER, input.publisher, required = true)?.let { errors[Field.PUBLISHER] = it }
        checkText(Field.ISSUE, input.issueNumber, required = true)?.let { errors[Field.ISSUE] = it }
        when {
            input.acquisitionDate == null -> errors[Field.ACQUISITION_DATE] = Error.REQUIRED
            input.acquisitionDate.isAfter(today) -> errors[Field.ACQUISITION_DATE] = Error.FUTURE_DATE
        }
        if (input.grade < MIN_GRADE || input.grade > MAX_GRADE) errors[Field.GRADE] = Error.OUT_OF_RANGE
        checkText(Field.NOTES, input.notes, required = false)?.let { errors[Field.NOTES] = it }
        checkText(Field.CERTIFICATION, input.certification, required = false)?.let {
            errors[Field.CERTIFICATION] = it
        }
        return errors
    }

    private fun checkText(field: Field, value: String, required: Boolean): Error? {
        val trimmed = value.trim()
        return when {
            required && trimmed.isEmpty() -> Error.REQUIRED
            trimmed.length > maxLength(field) -> Error.TOO_LONG
            else -> null
        }
    }
}
