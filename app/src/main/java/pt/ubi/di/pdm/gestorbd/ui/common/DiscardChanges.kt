package pt.ubi.di.pdm.gestorbd.ui.common

import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import pt.ubi.di.pdm.gestorbd.R

/**
 * Pede confirmação antes de sair de um formulário com alterações por gravar.
 * Regista-se em onCreate; [isDirty] compara o estado atual com o baseline do ViewModel.
 */
class DiscardChangesGuard(
    private val activity: AppCompatActivity,
    private val isDirty: () -> Boolean,
) {
    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = confirmOrFinish()
    }

    init {
        activity.onBackPressedDispatcher.addCallback(activity, callback)
    }

    fun onNavigateUp(): Boolean {
        confirmOrFinish()
        return true
    }

    private fun confirmOrFinish() {
        if (!isDirty()) {
            activity.finish()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.discard_title)
            .setMessage(R.string.discard_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_discard) { _, _ -> activity.finish() }
            .show()
    }
}
