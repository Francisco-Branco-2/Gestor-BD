package pt.ubi.di.pdm.gestorbd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator.Error
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator.Field

class FolderValidatorTest {

    private val valid = FolderValidator.Input(name = "Tintin", authors = "Hergé")

    @Test
    fun validInputHasNoErrors() {
        assertTrue(FolderValidator.validate(valid).isEmpty())
    }

    @Test
    fun blankFieldsAreRequired() {
        val errors = FolderValidator.validate(FolderValidator.Input(name = "  ", authors = ""))
        assertEquals(Error.REQUIRED, errors[Field.NAME])
        assertEquals(Error.REQUIRED, errors[Field.AUTHORS])
    }

    @Test
    fun tooLongNameIsRejected() {
        val errors = FolderValidator.validate(valid.copy(name = "a".repeat(81)))
        assertEquals(Error.TOO_LONG, errors[Field.NAME])
    }
}
