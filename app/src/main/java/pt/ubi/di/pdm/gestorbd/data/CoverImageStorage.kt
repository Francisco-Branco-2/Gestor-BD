package pt.ubi.di.pdm.gestorbd.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Persistência das capas em memória interna da app (`filesDir/covers`).
 *
 * Motivos desta abordagem em vez de URI da galeria:
 * - a permissão de leitura do Photo Picker é temporária;
 * - o utilizador pode apagar a foto original;
 * - a BD fica com um nome estável, independente do caminho absoluto.
 */
class CoverImageStorage(context: Context) {

    private val coversDir = File(context.applicationContext.filesDir, COVERS_DIR)
    private val contentResolver = context.applicationContext.contentResolver

    /** Copia, redimensiona e comprime a imagem escolhida. Devolve o nome do ficheiro. */
    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(contentResolver, uri)
        // ImageDecoder aplica a rotação EXIF (fotos tiradas em modo retrato).
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val scale = MAX_DIMENSION_PX.toFloat() / maxOf(width, height)
            if (scale < 1f) {
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        coversDir.mkdirs()
        val fileName = "${UUID.randomUUID()}.jpg"
        val file = File(coversDir, fileName)
        try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    throw IOException("Falha ao comprimir a imagem")
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
        fileName
    }

    /** Descodifica já com inSampleSize para não estourar memória na lista. */
    suspend fun decodeSampled(fileName: String, maxSizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(fileName) ?: return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= maxSizePx && bounds.outHeight / (sampleSize * 2) >= maxSizePx) {
            sampleSize *= 2
        }
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    suspend fun delete(fileName: String?) {
        if (fileName == null) return
        withContext(Dispatchers.IO) { deleteNow(fileName) }
    }

    /** Versão síncrona para [androidx.lifecycle.ViewModel.onCleared], onde já não há corrotina. */
    fun deleteNow(fileName: String) {
        fileFor(fileName)?.delete()
    }

    private fun fileFor(fileName: String): File? {
        val file = File(coversDir, fileName)
        // Path traversal: só aceita ficheiros diretamente dentro de covers/.
        return file.takeIf { it.parentFile == coversDir && it.exists() }
    }

    private companion object {
        const val COVERS_DIR = "covers"
        const val MAX_DIMENSION_PX = 1200
        const val JPEG_QUALITY = 85
    }
}
