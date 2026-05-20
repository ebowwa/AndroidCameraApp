package com.camerapp

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.camerapp.databinding.FragmentNoConnectionSheetBinding

/**
 * Bottom sheet dialog for showing connection-related information.
 * Note: This should only be shown for features that actually require internet connectivity,
 * NOT for speech recognition which works offline.
 */
class NoConnectionSheet : DialogFragment() {

    private var _binding: FragmentNoConnectionSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNoConnectionSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.dismissButton.setOnClickListener {
            dismiss()
        }

        binding.retryButton.setOnClickListener {
            dismiss()
            // Trigger retry callback if provided
            onRetryAction?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private var onRetryAction: (() -> Unit)? = null

        fun newInstance(
            message: String = "No internet connection",
            showRetryButton: Boolean = true,
            retryAction: (() -> Unit)? = null
        ): NoConnectionSheet {
            onRetryAction = retryAction
            return NoConnectionSheet().apply {
                arguments = Bundle().apply {
                    putString("message", message)
                    putBoolean("show_retry_button", showRetryButton)
                }
            }
        }

        /**
         * Creates a sheet for cloud services that require internet.
         * Do NOT use this for speech recognition errors.
         */
        fun forCloudService(message: String = "Cloud services require internet connection"): NoConnectionSheet {
            return newInstance(
                message = message,
                showRetryButton = true,
                retryAction = null
            )
        }
    }
}