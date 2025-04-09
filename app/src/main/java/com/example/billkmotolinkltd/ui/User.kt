package com.example.billkmotolinkltd.ui

data class User(
    val userName: String = "",
    val email: String = "",
    val pendingAmount: Double = 0.0,
    val isWorkingOnSunday: Boolean = true,
    val dailyTarget: Double = 0.0,
    val isActive: Boolean? = null,
    val userRank: String = "",
    val currentInAppBalance: Double = 0.0,
    val sundayTarget: Double = 0.0,
)