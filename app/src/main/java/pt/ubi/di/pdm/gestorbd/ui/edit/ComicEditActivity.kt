package pt.ubi.di.pdm.gestorbd.ui.edit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.GestorBdApp
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.databinding.ActivityComicEditBinding
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator.Error
import pt.ubi.di.pdm.gestorbd.domain.ComicValidator.Field
import pt.ubi.di.pdm.gestorbd.ui.common.DiscardChangesGuard
import pt.ubi.di.pdm.gestorbd.ui.common.Formatters
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formulário partilhado para criar e editar (modo definido pelo extra [EXTRA_COMIC_ID]).
 * A capa usa o Photo Picker do sistema — sem permissões de armazenamento no manifesto.
 * Alterações por gravar pedem confirmação via [DiscardChangesGuard].
 */
class ComicEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComicEditBinding
    private val viewModel: ComicEditViewModel by viewModels { ComicEditViewModel.Factory }
    private var coverJob: Job? = null
    private lateinit var discardGuard: DiscardChangesGuard

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onCoverPicked)
    }

    /** Campo validado -> TextInputLayout onde o erro é mostrado. */
    private val fieldLayouts: Map<Field, TextInputLayout> by lazy {
        mapOf(
            Field.TITLE to binding.layoutTitle,
            Field.PUBLISHER to binding.layoutPublisher,
            Field.ISSUE to binding.layoutIssue,
            Field.ACQUISITION_DATE to binding.layoutDate,
            Field.CERTIFICATION to binding.layoutCertification,
            Field.NOTES to binding.layoutNotes,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComicEditBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(if (viewModel.isEditing) R.string.title_edit else R.string.title_new)

        discardGuard = DiscardChangesGuard(this) { viewModel.isDirty(currentText(), binding.sliderGrade.value.toDouble()) }

        setupForm()
        restoreDatePickerListener()
        observeViewModel()
    }

    private fun setupForm() = with(binding) {
        sliderGrade.setLabelFormatter { Formatters.grade(it.toDouble()) }
        sliderGrade.addOnChangeListener { _, value, _ -> updateGradeText(value) }
        updateGradeText(sliderGrade.value)

        editDate.setOnClickListener { showDatePicker() }
        layoutDate.setEndIconOnClickListener { showDatePicker() }

        buttonPickCover.setOnClickListener {
            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        buttonRemoveCover.setOnClickListener { viewModel.removeCover() }
        buttonSave.setOnClickListener { save() }

        // O erro de um campo desaparece assim que o utilizador o altera.
        fieldLayouts.values.forEach { layout -> layout.editText?.doAfterTextChanged { layout.error = null } }
    }

    private fun currentText() = ComicEditViewModel.BaselineText(
        title = binding.editTitle.text?.toString().orEmpty(),
        publisher = binding.editPublisher.text?.toString().orEmpty(),
        issue = binding.editIssue.text?.toString().orEmpty(),
        certification = binding.editCertification.text?.toString().orEmpty(),
        notes = binding.editNotes.text?.toString().orEmpty(),
    )

    private fun updateGradeText(value: Float) {
        binding.textGradeValue.text = Formatters.grade(value.toDouble())
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.acquisitionDate.collect { date ->
                        binding.editDate.setText(date?.let(Formatters::date).orEmpty())
                    }
                }
                launch { viewModel.coverFileName.collect(::renderCover) }
                launch {
                    viewModel.publishers.collect { publishers ->
                        binding.editPublisher.setAdapter(
                            ArrayAdapter(this@ComicEditActivity, android.R.layout.simple_list_item_1, publishers),
                        )
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun handleEvent(event: EditEvent) {
        when (event) {
            is EditEvent.Populate -> fillForm(event.comic)
            is EditEvent.ValidationFailed -> showErrors(event.errors)
            EditEvent.Saved -> {
                Toast.makeText(this, R.string.comic_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            EditEvent.SaveFailed -> Snackbar.make(binding.root, R.string.error_save, Snackbar.LENGTH_LONG).show()
            EditEvent.ImageFailed -> Snackbar.make(binding.root, R.string.error_image, Snackbar.LENGTH_LONG).show()
            EditEvent.NotFound -> {
                Toast.makeText(this, R.string.comic_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun renderCover(fileName: String?) {
        coverJob?.cancel()
        coverJob = (application as GestorBdApp).coverLoader.bind(
            binding.imageCover, fileName, lifecycleScope, resources.getDimensionPixelSize(R.dimen.cover_edit_height),
        )
        binding.buttonRemoveCover.isVisible = fileName != null
        binding.buttonPickCover.setText(if (fileName == null) R.string.action_pick_cover else R.string.action_change_cover)
    }

    private fun fillForm(comic: Comic) = with(binding) {
        editTitle.setText(comic.title)
        editPublisher.setText(comic.publisher, false)
        editIssue.setText(comic.issueNumber)
        sliderGrade.value = comic.grade.toFloat()
        editCertification.setText(comic.certification)
        editNotes.setText(comic.notes)
    }

    private fun save() = with(binding) {
        fieldLayouts.values.forEach { it.error = null }
        viewModel.save(
            ComicValidator.Input(
                title = editTitle.text.toString(),
                publisher = editPublisher.text.toString(),
                issueNumber = editIssue.text.toString(),
                grade = sliderGrade.value.toDouble(),
                acquisitionDate = viewModel.currentDate,
                notes = editNotes.text.toString(),
                certification = editCertification.text.toString(),
            ),
        )
    }

    private fun showErrors(errors: Map<Field, Error>) {
        errors.forEach { (field, error) -> fieldLayouts[field]?.error = errorMessage(field, error) }
        errors.keys.firstNotNullOfOrNull { fieldLayouts[it] }?.let { layout ->
            binding.scrollForm.post { binding.scrollForm.smoothScrollTo(0, layout.top) }
        }
    }

    private fun errorMessage(field: Field, error: Error): String = when (error) {
        Error.REQUIRED -> getString(R.string.error_required)
        Error.TOO_LONG -> ComicValidator.maxLength(field).let { resources.getQuantityString(R.plurals.error_too_long, it, it) }
        Error.FUTURE_DATE -> getString(R.string.error_future_date)
        Error.OUT_OF_RANGE -> getString(R.string.error_grade_range)
    }

    private fun showDatePicker() {
        if (supportFragmentManager.findFragmentByTag(DATE_PICKER_TAG) != null) return
        val selected = (viewModel.currentDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .setValidator(DateValidatorPointBackward.now())
            .build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.label_acquisition_date)
            .setSelection(selected)
            .setCalendarConstraints(constraints)
            .build()
        attachDateListener(picker)
        picker.show(supportFragmentManager, DATE_PICKER_TAG)
    }

    /** Ao rodar o ecrã o diálogo é recriado pelo sistema, mas o listener tem de ser associado de novo. */
    private fun restoreDatePickerListener() {
        @Suppress("UNCHECKED_CAST")
        (supportFragmentManager.findFragmentByTag(DATE_PICKER_TAG) as? MaterialDatePicker<Long>)?.let(::attachDateListener)
    }

    private fun attachDateListener(picker: MaterialDatePicker<Long>) {
        picker.addOnPositiveButtonClickListener { millis ->
            // O MaterialDatePicker devolve a meia-noite UTC do dia escolhido.
            viewModel.setAcquisitionDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
            binding.layoutDate.error = null
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) return discardGuard.onNavigateUp()
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_COMIC_ID = "comic_id"
        const val EXTRA_FOLDER_ID = "folder_id"
        private const val DATE_PICKER_TAG = "date_picker"

        fun newIntent(context: Context, folderId: Long, comicId: Long = 0L): Intent =
            Intent(context, ComicEditActivity::class.java)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .putExtra(EXTRA_COMIC_ID, comicId)
    }
}
