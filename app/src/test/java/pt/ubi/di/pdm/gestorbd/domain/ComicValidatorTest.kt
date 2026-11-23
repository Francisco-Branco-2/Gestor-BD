package pt.ubi.di.pdm.gestorbd.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator.Error
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator.Field
import java.time.LocalDate

class ComicValidatorTest {

    private val today = LocalDate.of(2026, 10, 31)

    private val valid = ComicValidator.Input(
        title = "Tintin no Tibete",
        publisher = "Asa",
        issueNumber = "20",
        grade = 9.5,
        acquisitionDate = today,
        notes = "",
    )

    @Test
    fun validInputHasNoErrors() {
        assertTrue(ComicValidator.validate(valid, today).isEmpty())
    }

    @Test
    fun blankRequiredFieldsAreReported() {
        val errors = ComicValidator.validate(valid.copy(title = "  ", publisher = "", issueNumber = ""), today)
        assertEquals(Error.REQUIRED, errors[Field.TITLE])
        assertEquals(Error.REQUIRED, errors[Field.PUBLISHER])
        assertEquals(Error.REQUIRED, errors[Field.ISSUE])
    }

    @Test
    fun tooLongTextIsReported() {
        assertEquals(Error.TOO_LONG, ComicValidator.validate(valid.copy(title = "a".repeat(121)), today)[Field.TITLE])
        assertEquals(Error.TOO_LONG, ComicValidator.validate(valid.copy(notes = "a".repeat(1001)), today)[Field.NOTES])
        assertEquals(
            Error.TOO_LONG,
            ComicValidator.validate(valid.copy(certification = "a".repeat(41)), today)[Field.CERTIFICATION],
        )
    }

    @Test
    fun optionalCertificationIsAcceptedWhenBlankOrShort() {
        assertTrue(ComicValidator.validate(valid.copy(certification = ""), today).isEmpty())
        assertTrue(ComicValidator.validate(valid.copy(certification = "CGC 9.8"), today).isEmpty())
    }

    @Test
    fun missingOrFutureDateIsRejected() {
        assertEquals(Error.REQUIRED, ComicValidator.validate(valid.copy(acquisitionDate = null), today)[Field.ACQUISITION_DATE])
        assertEquals(Error.FUTURE_DATE, ComicValidator.validate(valid.copy(acquisitionDate = today.plusDays(1)), today)[Field.ACQUISITION_DATE])
    }

    @Test
    fun gradeOutsideOneToTenIsRejected() {
        assertEquals(Error.OUT_OF_RANGE, ComicValidator.validate(valid.copy(grade = 0.5), today)[Field.GRADE])
        assertEquals(Error.OUT_OF_RANGE, ComicValidator.validate(valid.copy(grade = 10.5), today)[Field.GRADE])
        assertTrue(ComicValidator.validate(valid.copy(grade = 1.0), today).isEmpty())
        assertTrue(ComicValidator.validate(valid.copy(grade = 10.0), today).isEmpty())
    }
}
