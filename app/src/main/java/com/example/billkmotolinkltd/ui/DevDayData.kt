package com.example.billkmotolinkltd.ui

import java.util.Date

data class DevDayData(
    val dayName: String,
    val netIncome: Double,
    val grossIncome: Double,
    val netDeviation: Double,
    val grossDeviation: Double,
    val netGrossDifference: Double
)

data class DevUser(
    val userName: String,
    val days: List<DevDayData>
)

data class DevWeek(
    val weekName: String,
    val users: List<DevUser>,
    val startDate: Date?
)
