package com.billkmotolink.ltd.ui.weeklyreports

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class WeeklyreportsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Weekly reports"
    }
    val text: LiveData<String> = _text
}