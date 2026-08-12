package com.example.codcalculator

import java.time.LocalDate

data class DeliveryBoy(
    val id: String,
    val name: String,
)

data class CodDeposit(
    val deliveryBoy: DeliveryBoy,
    val date: LocalDate,
    val totalCollected: Double,
    val cashDeposited: Double,
    val onlineDeposited: Double,
) {
    val dueAmount: Double = totalCollected - cashDeposited - onlineDeposited
}
