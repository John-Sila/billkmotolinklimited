package com.billkmotolink.ltd.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {

    val email = MutableLiveData("")
    val password = MutableLiveData("")

    private val _loginStatus = MutableLiveData<Boolean>()
    val loginStatus: LiveData<Boolean> = _loginStatus

    fun login() {
        val emailValue = email.value ?: ""
        val passwordValue = password.value ?: ""

        // Simulating simple validation
        _loginStatus.value = emailValue == "admin@example.com" && passwordValue == "password123"
    }
}
