package pt.ubi.di.pdm.gestorbd.ui.folders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.FolderWithCount
import pt.ubi.di.pdm.gestorbd.databinding.ItemFolderBinding
import pt.ubi.di.pdm.gestorbd.ui.common.CoverLoader

/** Linha da lista de coleções (capa, nome, autores, nº de exemplares). */
class FolderAdapter(
    private val scope: CoroutineScope,
    private val coverLoader: CoverLoader,
    private val onClick: (FolderWithCount) -> Unit,
) : ListAdapter<FolderWithCount, FolderAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(val binding: ItemFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        var coverJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        with(holder.binding) {
            val countLabel = context.resources.getQuantityString(
                R.plurals.comic_count,
                item.comicCount,
                item.comicCount,
            )
            textName.text = item.folder.name
            textAuthors.text = item.folder.authors
            textCount.text = countLabel
            root.contentDescription = context.getString(
                R.string.folder_list_item_cd,
                item.folder.name,
                item.folder.authors,
                countLabel,
            )
            root.setOnClickListener { onClick(item) }
            holder.coverJob?.cancel()
            val thumbSize = context.resources.getDimensionPixelSize(R.dimen.cover_thumb_height)
            holder.coverJob = coverLoader.bind(imageCover, item.folder.coverFileName, scope, thumbSize)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.coverJob?.cancel()
        super.onViewRecycled(holder)
    }

    private object DiffCallback : DiffUtil.ItemCallback<FolderWithCount>() {
        override fun areItemsTheSame(oldItem: FolderWithCount, newItem: FolderWithCount) =
            oldItem.folder.id == newItem.folder.id

        override fun areContentsTheSame(oldItem: FolderWithCount, newItem: FolderWithCount) =
            oldItem == newItem
    }
}
