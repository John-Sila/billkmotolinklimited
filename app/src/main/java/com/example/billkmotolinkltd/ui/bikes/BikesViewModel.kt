package com.example.billkmotolinkltd.ui.bikes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BikesViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Bike Management"
    }
    val text: LiveData<String> = _text
}