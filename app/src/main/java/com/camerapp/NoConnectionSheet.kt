package com.camerapp

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NoConnectionSheet : BottomSheetDialogFragment() {

    interface OnDismissListener {
        fun onDismissClick()
    }

    private var dismissListener: OnDismissListener? = null

    fun setOnDismissListener(listener: OnDismissListener) {
        dismissListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Make it non-cancelable so user has to acknowledge
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_no_connection_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dismissButton = view.findViewById<android.widget.Button>(R.id.dismissButton)
        val retryButton = view.findViewById<android.widget.Button>(R.id.retryButton)
        val connectionTypeText = view.findViewById<TextView>(R.id.connectionTypeText)

        // Update connection type
        val context = requireContext()
        val connectionType = NetworkUtils.getConnectionType(context)
        connectionTypeText.text = "Connection Type: $connectionType"

        dismissButton.setOnClickListener {
            dismissListener?.onDismissClick()
            dismiss()
        }

        retryButton.setOnClickListener {
            // Refresh connection status
            val newConnectionType = NetworkUtils.getConnectionType(context)
            connectionTypeText.text = "Connection Type: $newConnectionType"

            // If connected, dismiss the sheet
            if (NetworkUtils.isConnected.value) {
                dismiss()
            }
        }
    }

    companion object {
        private const val TAG = "NoConnectionSheet"

        fun show(fragmentManager: FragmentManager): NoConnectionSheet {
            val sheet = NoConnectionSheet()
            sheet.show(fragmentManager, TAG)
            return sheet
        }
    }
}