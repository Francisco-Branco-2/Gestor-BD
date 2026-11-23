package pt.ubi.di.pdm.gestorbd.ui.folders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.GestorBdApp
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.BdFolder
import pt.ubi.di.pdm.gestorbd.databinding.ActivityFolderEditBinding
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator.Error
import pt.ubi.di.pdm.gestorbd.domain.FolderValidator.Field
import pt.ubi.di.pdm.gestorbd.ui.common.DiscardChangesGuard
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge

/** Formulário para criar ou editar uma pasta/coleção (nome, autores, capa). */
class FolderEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFolderEditBinding
    private val viewModel: FolderEditViewModel by viewModels { FolderEditViewModel.Factory }
    private var coverJob: Job? = null
    private lateinit var discardGuard: DiscardChangesGuard

    private val pickCover = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onCoverPicked)
    }

    private val fieldLayouts: Map<Field, TextInputLayout> by lazy {
        mapOf(
            Field.NAME to binding.layoutName,
            Field.AUTHORS to binding.layoutAuthors,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFolderEditBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(if (viewModel.isEditing) R.string.title_edit_folder else R.string.title_new_folder)

        discardGuard = DiscardChangesGuard(this) {
            viewModel.isDirty(
                binding.editName.text?.toString().orEmpty(),
                binding.editAuthors.text?.toString().orEmpty(),
            )
        }

        binding.buttonPickCover.setOnClickListener {
            pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.buttonRemoveCover.setOnClickListener { viewModel.removeCover() }
        binding.buttonSave.setOnClickListener { save() }
        fieldLayouts.values.forEach { layout -> layout.editText?.doAfterTextChanged { layout.error = null } }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.coverFileName.collect(::renderCover) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun handleEvent(event: FolderEditEvent) {
        when (event) {
            is FolderEditEvent.Populate -> fillForm(event.folder)
            is FolderEditEvent.ValidationFailed -> showErrors(event.errors)
            FolderEditEvent.Saved -> {
                Toast.makeText(this, R.string.folder_saved, Toast.LENGTH_SHORT).show()
                finish()
            }
            FolderEditEvent.SaveFailed -> Snackbar.make(binding.root, R.string.error_save, Snackbar.LENGTH_LONG).show()
            FolderEditEvent.ImageFailed -> Snackbar.make(binding.root, R.string.error_image, Snackbar.LENGTH_LONG).show()
            FolderEditEvent.NotFound -> {
                Toast.makeText(this, R.string.folder_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun renderCover(fileName: String?) {
        coverJob?.cancel()
        coverJob = (application as GestorBdApp).coverLoader.bind(
            binding.imageCover,
            fileName,
            lifecycleScope,
            resources.getDimensionPixelSize(R.dimen.cover_edit_height),
        )
        binding.buttonRemoveCover.isVisible = fileName != null
        binding.buttonPickCover.setText(
            if (fileName == null) R.string.action_pick_cover else R.string.action_change_cover,
        )
    }

    private fun fillForm(folder: BdFolder) {
        binding.editName.setText(folder.name)
        binding.editAuthors.setText(folder.authors)
    }

    private fun save() {
        fieldLayouts.values.forEach { it.error = null }
        viewModel.save(
            FolderValidator.Input(
                name = binding.editName.text.toString(),
                authors = binding.editAuthors.text.toString(),
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
        Error.TOO_LONG -> {
            val max = FolderValidator.maxLength(field)
            resources.getQuantityString(R.plurals.error_too_long, max, max)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) return discardGuard.onNavigateUp()
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"

        fun newIntent(context: Context, folderId: Long = 0L): Intent =
            Intent(context, FolderEditActivity::class.java).putExtra(EXTRA_FOLDER_ID, folderId)
    }
}
