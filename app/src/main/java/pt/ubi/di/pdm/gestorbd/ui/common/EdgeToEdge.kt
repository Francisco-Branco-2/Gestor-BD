package pt.ubi.di.pdm.gestorbd.ui.common

import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * A partir do Android 15 as apps são desenhadas por baixo das barras do sistema.
 * Ativa esse modo em todas as versões e acrescenta padding para que nada fique tapado
 * (incluindo pelo teclado, que deixa de redimensionar a janela automaticamente).
 *
 * Em alguns fabricantes (ex.: ColorOS com gestos), [WindowInsetsCompat.Type.systemBars]
 * pode reportar 0 na base; [mandatorySystemGestures] cobre a área do indicador.
 */
fun AppCompatActivity.setContentViewEdgeToEdge(root: View) {
    enableEdgeToEdge()
    setContentView(root)
    val initialLeft = root.paddingLeft
    val initialTop = root.paddingTop
    val initialRight = root.paddingRight
    val initialBottom = root.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = initialLeft + bars.left,
            top = initialTop + bars.top,
            right = initialRight + bars.right,
            bottom = initialBottom + maxOf(bars.bottom, gestures.bottom, ime.bottom),
        )
        WindowInsetsCompat.CONSUMED
    }
}
