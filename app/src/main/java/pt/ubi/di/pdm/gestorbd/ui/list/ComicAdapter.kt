package pt.ubi.di.pdm.gestorbd.ui.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.Comic
import pt.ubi.di.pdm.gestorbd.databinding.ItemComicBinding
import pt.ubi.di.pdm.gestorbd.ui.common.CoverLoader
import pt.ubi.di.pdm.gestorbd.ui.common.Formatters

/**
 * Lista de exemplares com [ListAdapter] + DiffUtil (atualizações parciais eficientes).
 * O carregamento da capa é cancelado em [onViewRecycled] para não desperdiçar CPU
 * quando a célula sai do ecrã durante o scroll.
 */
class ComicAdapter(
    private val scope: CoroutineScope,
    private val coverLoader: CoverLoader,
    private val onClick: (Comic) -> Unit,
) : ListAdapter<Comic, ComicAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemComicBinding) : RecyclerView.ViewHolder(binding.root) {
        var coverJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemComicBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) onClick(getItem(position))
        }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comic = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            val gradeLabel = Formatters.grade(comic.grade)
            textTitle.text = comic.title
            textSubtitle.text = context.getString(R.string.comic_issue_publisher, comic.issueNumber, comic.publisher)
            textGrade.text = gradeLabel
            textGrade.contentDescription = context.getString(R.string.cd_grade, gradeLabel)
            root.contentDescription = context.getString(
                R.string.comic_list_item_cd,
                comic.title,
                comic.issueNumber,
                comic.publisher,
                gradeLabel,
            )

            holder.coverJob?.cancel()
            val thumbSize = context.resources.getDimensionPixelSize(R.dimen.cover_thumb_height)
            holder.coverJob = coverLoader.bind(imageCover, comic.coverFileName, scope, thumbSize)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.coverJob?.cancel()
        super.onViewRecycled(holder)
    }

    private object DiffCallback : DiffUtil.ItemCallback<Comic>() {
        override fun areItemsTheSame(oldItem: Comic, newItem: Comic) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Comic, newItem: Comic) = oldItem == newItem
    }
}
