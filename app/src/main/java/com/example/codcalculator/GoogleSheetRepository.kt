package com.example.codcalculator

import java.time.LocalDate

/**
 * Replace this in-memory implementation with the Google Sheets API after adding OAuth consent.
 * Expected spreadsheet tabs:
 * - DeliveryBoys: id, name
 * - CodEntries: date, deliveryBoyId, deliveryBoyName, total, cash, online, due
 */
interface GoogleSheetRepository {
    fun deliveryBoys(): List<DeliveryBoy>
    fun entries(): List<CodDeposit>
    fun save(entry: CodDeposit)
}

class InMemoryGoogleSheetRepository : GoogleSheetRepository {
    private val boys = listOf(
        DeliveryBoy("DB001", "Select from Google Sheet"),
        DeliveryBoy("DB002", "Amit Kumar"),
        DeliveryBoy("DB003", "Rahul Singh"),
    )

    private val deposits = mutableListOf(
        CodDeposit(boys[1], LocalDate.now(), 2500.0, 1000.0, 500.0),
        CodDeposit(boys[2], LocalDate.now(), 1800.0, 800.0, 400.0),
    )

    override fun deliveryBoys(): List<DeliveryBoy> = boys

    override fun entries(): List<CodDeposit> = deposits.toList()

    override fun save(entry: CodDeposit) {
        deposits.add(entry)
        // TODO: Append this row to Google Sheets once Drive/Sheets authorization is connected.
    }
}
