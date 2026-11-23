package pt.ubi.di.pdm.gestorbd.ui.folders

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDividerItemDecoration
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.GestorBdApp
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.FolderWithCount
import pt.ubi.di.pdm.gestorbd.databinding.ActivityMainBinding
import pt.ubi.di.pdm.gestorbd.ui.common.ThemeMode
import pt.ubi.di.pdm.gestorbd.ui.common.ThemePreferences
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge
import pt.ubi.di.pdm.gestorbd.ui.list.ComicListActivity

/**
 * Ecrã inicial (launcher): lista de pastas/coleções.
 * Ao tocar numa pasta, abre [ComicListActivity] com os seus exemplares.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: FolderAdapter
    private val viewModel: FolderListViewModel by viewModels { FolderListViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = FolderAdapter(lifecycleScope, (application as GestorBdApp).coverLoader) { item ->
            startActivity(ComicListActivity.newIntent(this@MainActivity, item.folder.id))
        }
        binding.recyclerFolders.layoutManager = LinearLayoutManager(this)
        binding.recyclerFolders.addItemDecoration(
            MaterialDividerItemDecoration(this, MaterialDividerItemDecoration.VERTICAL),
        )
        binding.recyclerFolders.adapter = adapter
        binding.fabAdd.setOnClickListener {
            startActivity(FolderEditActivity.newIntent(this))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.folders.collect { folders -> folders?.let(::render) }
            }
        }
    }

    private fun render(folders: List<FolderWithCount>) {
        adapter.submitList(folders)
        binding.textEmpty.isVisible = folders.isEmpty()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_folders, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_theme -> {
            showThemeDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showThemeDialog() {
        val preferences = ThemePreferences(this)
        val options = listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map(::getString)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_theme)
            .setSingleChoiceItems(options.toTypedArray(), preferences.themeMode.ordinal) { dialog, which ->
                preferences.themeMode = ThemeMode.entries[which]
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
