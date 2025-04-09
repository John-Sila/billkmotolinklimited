package com.example.billkmotolinkltd.ui.incidences

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class IncidencesViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Incidences"
    }
    val text: LiveData<String> = _text
}