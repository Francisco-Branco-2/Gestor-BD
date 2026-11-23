package pt.ubi.di.pdm.gestorbd.domain

/**
 * Validação da pasta/coleção (nome + autores).
 * Mesmo padrão do [ComicValidator]: lógica pura, testável sem Android.
 */
object FolderValidator {

    enum class Field { NAME, AUTHORS }

    enum class Error { REQUIRED, TOO_LONG }

    data class Input(val name: String, val authors: String)

    fun maxLength(field: Field): Int = when (field) {
        Field.NAME -> 80
        Field.AUTHORS -> 120
    }

    fun validate(input: Input): Map<Field, Error> {
        val errors = linkedMapOf<Field, Error>()
        checkText(Field.NAME, input.name)?.let { errors[Field.NAME] = it }
        checkText(Field.AUTHORS, input.authors)?.let { errors[Field.AUTHORS] = it }
        return errors
    }

    private fun checkText(field: Field, value: String): Error? {
        val trimmed = value.trim()
        return when {
            trimmed.isEmpty() -> Error.REQUIRED
            trimmed.length > maxLength(field) -> Error.TOO_LONG
            else -> null
        }
    }
}
