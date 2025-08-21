package com.example.billkmotolinkltd.ui.require_reclock

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RequireReclockViewModel: ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "Require"
    }
    val text: LiveData<String> = _text
}