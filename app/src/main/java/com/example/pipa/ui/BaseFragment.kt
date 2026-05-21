package com.example.pipa.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment

open class BaseFragment : Fragment() {
    fun hideKeyboard() {
        val view = activity?.currentFocus
        if (view != null) {
            val ims = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ims.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}