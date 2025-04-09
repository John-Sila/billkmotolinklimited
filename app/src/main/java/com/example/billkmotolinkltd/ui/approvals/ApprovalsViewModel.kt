package com.example.billkmotolinkltd.ui.approvals

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ApprovalsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Approvals"
    }
    val text: LiveData<String> = _text
}