package pt.ubi.di.pdm.gestorbd.ui.detail

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.GestorBdApp
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.databinding.ActivityComicDetailBinding
import pt.ubi.di.pdm.gestorbd.ui.common.Formatters
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge
import pt.ubi.di.pdm.gestorbd.ui.edit.ComicEditActivity

/** Detalhe de um exemplar: consulta, edição e eliminação com diálogo de confirmação. */
class ComicDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComicDetailBinding
    private val viewModel: ComicDetailViewModel by viewModels { ComicDetailViewModel.Factory }
    private var coverJob: Job? = null
    /** Referência atual para os botões Editar/Eliminar (listeners registados uma vez em onCreate). */
    private var displayedComic: Comic? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComicDetailBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.rowGrade.textLabel.setText(R.string.label_grade)
        binding.rowCertification.textLabel.setText(R.string.label_certification)
        binding.rowDate.textLabel.setText(R.string.label_acquisition_date)
        binding.rowNotes.textLabel.setText(R.string.label_notes)
        binding.buttonEdit.setOnClickListener {
            displayedComic?.let { comic ->
                startActivity(ComicEditActivity.newIntent(this, comic.folderId, comic.id))
            }
        }
        binding.buttonDelete.setOnClickListener { displayedComic?.let(::confirmDelete) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::renderState) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun renderState(state: DetailUiState) {
        when (state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> {
                if (!viewModel.isDeleting) Toast.makeText(this, R.string.comic_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
            is DetailUiState.Loaded -> render(state.comic)
        }
    }

    private fun handleEvent(event: DetailEvent) {
        when (event) {
            DetailEvent.Deleted -> {
                Toast.makeText(this, R.string.comic_deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
            DetailEvent.DeleteFailed -> Snackbar.make(binding.root, R.string.error_delete, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun render(comic: Comic) = with(binding) {
        displayedComic = comic
        textTitle.text = comic.title
        textSubtitle.text = getString(R.string.comic_issue_publisher, comic.issueNumber, comic.publisher)
        rowGrade.textValue.text = Formatters.grade(comic.grade)
        rowCertification.textValue.text =
            comic.certification.ifBlank { getString(R.string.no_certification) }
        rowDate.textValue.text = Formatters.date(comic.acquisitionDate)
        rowNotes.textValue.text = comic.notes.ifBlank { getString(R.string.no_notes) }

        coverJob?.cancel()
        coverJob = (application as GestorBdApp).coverLoader.bind(
            imageCover,
            comic.coverFileName,
            lifecycleScope,
            resources.getDimensionPixelSize(R.dimen.cover_detail_height),
            ImageView.ScaleType.FIT_CENTER,
        )
    }

    private fun confirmDelete(comic: Comic) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, comic.title, comic.issueNumber))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_COMIC_ID = "comic_id"

        fun newIntent(context: Context, comicId: Long): Intent =
            Intent(context, ComicDetailActivity::class.java).putExtra(EXTRA_COMIC_ID, comicId)
    }
}
