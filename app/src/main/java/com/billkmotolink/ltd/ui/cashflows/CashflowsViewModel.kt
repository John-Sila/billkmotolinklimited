package com.billkmotolink.ltd.ui.cashflows

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CashflowsViewModel: ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "Cash flows"
    }
    val text: LiveData<String> = _text
}