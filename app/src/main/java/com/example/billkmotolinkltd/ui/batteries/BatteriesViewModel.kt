package com.example.billkmotolinkltd.ui.batteries

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BatteriesViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Bike Management"
    }
    val text: LiveData<String> = _text
}