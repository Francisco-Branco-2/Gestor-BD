package pt.ubi.di.pdm.gestorbd.ui.list

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.GestorBdApp
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.BdFolder
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.data.ComicCsvExporter
import pt.ubi.di.pdm.gestorbd.data.ComicFilters
import pt.ubi.di.pdm.gestorbd.data.GradeFilter
import pt.ubi.di.pdm.gestorbd.data.SortOrder
import pt.ubi.di.pdm.gestorbd.databinding.ActivityComicListBinding
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge
import pt.ubi.di.pdm.gestorbd.ui.detail.ComicDetailActivity
import pt.ubi.di.pdm.gestorbd.ui.edit.ComicEditActivity
import pt.ubi.di.pdm.gestorbd.ui.folders.FolderEditActivity
import pt.ubi.di.pdm.gestorbd.ui.stats.StatsActivity
import java.io.File

/**
 * Exemplares de uma pasta: pesquisa, filtros, ordenação, exportação CSV e gestão da pasta.
 */
class ComicListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityComicListBinding
    private lateinit var adapter: ComicAdapter
    private val viewModel: ComicListViewModel by viewModels { ComicListViewModel.Factory }
    private var currentFolder: BdFolder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComicListBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        adapter = ComicAdapter(lifecycleScope, (application as GestorBdApp).coverLoader) { comic ->
            startActivity(ComicDetailActivity.newIntent(this, comic.id))
        }
        val span = resources.getInteger(R.integer.comic_grid_span)
        binding.recyclerComics.layoutManager = GridLayoutManager(this, span)
        if (span == 1) {
            binding.recyclerComics.addItemDecoration(
                MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
            )
        }
        binding.recyclerComics.adapter = adapter

        binding.chipPublisher.setOnClickListener { showPublisherDialog() }
        binding.chipGrade.setOnClickListener { showGradeDialog() }
        binding.chipSort.setOnClickListener { showSortDialog() }
        binding.fabAdd.setOnClickListener {
            startActivity(ComicEditActivity.newIntent(this, viewModel.folderId))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.folder.collect(::renderFolder) }
                launch { viewModel.comics.collect { comics -> comics?.let(::renderComics) } }
                launch { viewModel.filters.collect(::renderFilters) }
                launch {
                    viewModel.folderDeleted.collect { deleted ->
                        if (deleted) {
                            Toast.makeText(this@ComicListActivity, R.string.folder_deleted, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun renderFolder(folder: BdFolder?) {
        when {
            folder != null -> {
                currentFolder = folder
                supportActionBar?.title = folder.name
            }
            viewModel.folderDeleted.value -> Unit
            currentFolder != null -> {
                Toast.makeText(this, R.string.folder_not_found, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun renderComics(comics: List<Comic>) {
        adapter.submitList(comics)
        binding.textEmpty.isVisible = comics.isEmpty()
        binding.textEmpty.setText(
            if (viewModel.filters.value.isFiltering) R.string.empty_filtered else R.string.empty_comics,
        )
    }

    private fun renderFilters(filters: ComicFilters) {
        binding.chipPublisher.text = filters.publisher ?: getString(R.string.filter_all_publishers)
        binding.chipPublisher.isChecked = filters.publisher != null
        binding.chipGrade.text = getString(filters.grade.labelRes())
        binding.chipGrade.isChecked = filters.grade != GradeFilter.ALL
        binding.chipSort.text = getString(filters.sort.labelRes())
        binding.chipSort.isChecked = filters.sort != SortOrder.TITLE
    }

    private fun showPublisherDialog() {
        val publishers = viewModel.publishers.value
        val options = listOf(getString(R.string.filter_all_publishers)) + publishers
        val selected = viewModel.filters.value.publisher?.let { publishers.indexOf(it) + 1 } ?: 0
        showSingleChoiceDialog(R.string.filter_publisher_title, options, selected) { which ->
            viewModel.setPublisher(if (which == 0) null else publishers[which - 1])
        }
    }

    private fun showGradeDialog() {
        val options = GradeFilter.entries.map { getString(it.labelRes()) }
        showSingleChoiceDialog(R.string.filter_grade_title, options, viewModel.filters.value.grade.ordinal) { which ->
            viewModel.setGrade(GradeFilter.entries[which])
        }
    }

    private fun showSortDialog() {
        val options = SortOrder.entries.map { getString(it.labelRes()) }
        showSingleChoiceDialog(R.string.sort_title, options, viewModel.filters.value.sort.ordinal) { which ->
            viewModel.setSort(SortOrder.entries[which])
        }
    }

    private fun showSingleChoiceDialog(title: Int, options: List<String>, selected: Int, onSelected: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selected) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteFolder() {
        val folder = currentFolder ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_folder_title)
            .setMessage(getString(R.string.delete_folder_message, folder.name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteFolder() }
            .show()
    }

    /** Escreve CSV em cache e abre o seletor de partilha do sistema. */
    private fun exportCsv() {
        val folder = currentFolder ?: return
        lifecycleScope.launch {
            try {
                val comics = viewModel.comicsForExport()
                if (comics.isEmpty()) {
                    Toast.makeText(this@ComicListActivity, R.string.export_empty, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val dir = File(cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "${folder.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.csv")
                file.writeText(ComicCsvExporter.toCsv(comics), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(
                    this@ComicListActivity,
                    "${packageName}.fileprovider",
                    file,
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject, folder.name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, getString(R.string.export_chooser)))
            } catch (_: Exception) {
                Toast.makeText(this@ComicListActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comic_list, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        val currentQuery = viewModel.filters.value.query
        if (currentQuery.isNotEmpty()) {
            searchItem.expandActionView()
            searchView.setQuery(currentQuery, false)
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.setQuery(newText)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        R.id.action_stats -> {
            startActivity(StatsActivity.newIntent(this, viewModel.folderId))
            true
        }
        R.id.action_export -> {
            exportCsv()
            true
        }
        R.id.action_edit_folder -> {
            startActivity(FolderEditActivity.newIntent(this, viewModel.folderId))
            true
        }
        R.id.action_delete_folder -> {
            confirmDeleteFolder()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"

        fun newIntent(context: Context, folderId: Long): Intent =
            Intent(context, ComicListActivity::class.java).putExtra(EXTRA_FOLDER_ID, folderId)
    }
}

private fun GradeFilter.labelRes(): Int = when (this) {
    GradeFilter.ALL -> R.string.grade_filter_all
    GradeFilter.HIGH -> R.string.grade_filter_high
    GradeFilter.MEDIUM -> R.string.grade_filter_medium
    GradeFilter.LOW -> R.string.grade_filter_low
}

private fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.sort_by_title
    SortOrder.GRADE_DESC -> R.string.sort_by_grade
    SortOrder.DATE_DESC -> R.string.sort_by_date
}
