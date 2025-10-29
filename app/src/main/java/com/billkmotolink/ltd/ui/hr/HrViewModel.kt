package com.billkmotolink.ltd.ui.hr

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HrViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Human Resource"
    }
    val text: LiveData<String> = _text
}