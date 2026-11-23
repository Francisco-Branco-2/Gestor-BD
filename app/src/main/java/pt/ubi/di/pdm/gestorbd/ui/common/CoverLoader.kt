package pt.ubi.di.pdm.gestorbd.ui.common

import android.graphics.Bitmap
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.ubi.di.pdm.gestorbd.R
import pt.ubi.di.pdm.gestorbd.data.CoverImageStorage

/**
 * Carrega capas em background com cache LRU em memória.
 *
 * Sem biblioteca de imagens (Glide/Coil) de propósito: o enunciado valoriza
 * soluções compreensíveis e com poucas dependências externas.
 * A tag [R.id.cover_bind_key] evita mostrar a capa errada após reciclagem do ViewHolder.
 */
class CoverLoader(private val storage: CoverImageStorage) {

    private val cache = object : LruCache<String, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /**
     * Mostra a capa no [target] (ou o marcador por omissão se não existir).
     * Devolve o Job para que possa ser cancelado quando a vista é reciclada.
     */
    fun bind(
        target: ImageView,
        fileName: String?,
        scope: CoroutineScope,
        maxSizePx: Int,
        bitmapScaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
    ): Job? {
        // Guarda o pedido atual na ImageView para ignorar resultados atrasados após reciclagem.
        target.setTag(R.id.cover_bind_key, fileName)
        showPlaceholder(target)
        if (fileName == null) return null
        val key = "$fileName@$maxSizePx"
        cache.get(key)?.let {
            if (target.getTag(R.id.cover_bind_key) == fileName) showBitmap(target, it, bitmapScaleType)
            return null
        }
        return scope.launch {
            val bitmap = storage.decodeSampled(fileName, maxSizePx) ?: return@launch
            cache.put(key, bitmap)
            if (target.getTag(R.id.cover_bind_key) == fileName) {
                showBitmap(target, bitmap, bitmapScaleType)
            }
        }
    }

    private fun showPlaceholder(target: ImageView) {
        target.scaleType = ImageView.ScaleType.CENTER
        target.setImageResource(R.drawable.ic_cover_placeholder)
    }

    private fun showBitmap(target: ImageView, bitmap: Bitmap, scaleType: ImageView.ScaleType) {
        target.scaleType = scaleType
        target.setImageBitmap(bitmap)
    }

    private companion object {
        fun cacheSizeKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    }
}
