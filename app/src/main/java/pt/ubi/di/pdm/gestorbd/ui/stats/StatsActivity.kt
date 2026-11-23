package pt.ubi.di.pdm.gestorbd.ui.stats

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.databinding.ActivityStatsBinding
import pt.ubi.di.pdm.gestorbd.databinding.ItemStatBarBinding
import pt.ubi.di.pdm.gestorbd.ui.common.Formatters
import pt.ubi.di.pdm.gestorbd.ui.common.setContentViewEdgeToEdge
import java.text.NumberFormat

/**
 * Estatísticas simples da pasta (total, média, barras por editora e por grau).
 * Sem biblioteca de gráficos: usa [LinearProgressIndicator] com progresso relativo ao máximo.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val viewModel: StatsViewModel by viewModels { StatsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentViewEdgeToEdge(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> state?.let(::render) }
            }
        }
    }

    private fun render(state: StatsUiState) {
        val total = state.summary.total
        val hasData = total > 0
        binding.textTotal.text = resources.getQuantityString(R.plurals.comic_count, total, total)
        binding.textAverage.isVisible = hasData
        state.summary.averageGrade?.let { binding.textAverage.text = getString(R.string.stats_average, Formatters.grade(it)) }
        binding.groupCharts.isVisible = hasData

        if (hasData) {
            renderBars(binding.containerPublishers, state.publishers.map { it.publisher to it.total })
            renderBars(binding.containerGrades, state.grades.map { getString(R.string.grade_bucket, it.bucket) to it.total })
        }
    }

    /** Barras horizontais simples, sem bibliotecas externas. */
    private fun renderBars(container: LinearLayout, bars: List<Pair<String, Int>>) {
        container.removeAllViews()
        val max = bars.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: return
        val numberFormat = NumberFormat.getIntegerInstance()
        val inflater = LayoutInflater.from(this)
        bars.forEach { (label, value) ->
            val row = ItemStatBarBinding.inflate(inflater, container, true)
            row.textLabel.text = label
            row.textValue.text = numberFormat.format(value)
            row.progressBar.max = max
            row.progressBar.setProgressCompat(value, false)
            row.root.contentDescription = getString(R.string.cd_stat_bar, label, value)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_FOLDER_ID = "folder_id"

        fun newIntent(context: Context, folderId: Long): Intent =
            Intent(context, StatsActivity::class.java).putExtra(EXTRA_FOLDER_ID, folderId)
    }
}
