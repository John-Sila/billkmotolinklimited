package com.example.billkmotolinkltd.ui.dailyreports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DailyreportsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Daily Reports"
    }
    val text: LiveData<String> = _text
}