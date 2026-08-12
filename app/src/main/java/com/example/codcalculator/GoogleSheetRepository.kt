package com.example.codcalculator

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import java.time.LocalDate

interface GoogleSheetRepository {
    fun deliveryBoys(): List<DeliveryBoy>
    fun entries(): List<CodDeposit>
    fun save(entry: CodDeposit)
}

class DriveBackedGoogleSheetRepository(
    context: Context,
    account: GoogleSignInAccount,
) : GoogleSheetRepository {
    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val credential = GoogleAccountCredential.usingOAuth2(
        context,
        listOf(DriveScopes.DRIVE_FILE, SheetsScopes.SPREADSHEETS),
    ).apply { selectedAccount = account.account }

    private val drive = Drive.Builder(transport, jsonFactory, credential)
        .setApplicationName(APP_NAME)
        .build()
    private val sheets = Sheets.Builder(transport, jsonFactory, credential)
        .setApplicationName(APP_NAME)
        .build()

    private val spreadsheetId: String by lazy { ensureDeliveryBoySpreadsheet() }

    override fun deliveryBoys(): List<DeliveryBoy> {
        ensureDeliveryBoyHeader()
        val rows = sheets.spreadsheets().values()
            .get(spreadsheetId, "$DELIVERY_BOY_SHEET!A2:B")
            .execute()
            .getValues()
            .orEmpty()

        return rows.mapIndexedNotNull { index, row ->
            val name = row.getOrNull(1)?.toString()?.trim()
                ?: row.getOrNull(0)?.toString()?.trim()
            name?.takeIf { it.isNotBlank() }?.let {
                DeliveryBoy(row.getOrNull(0)?.toString().orEmpty().ifBlank { "DB${index + 1}" }, it)
            }
        }
    }

    override fun entries(): List<CodDeposit> {
        ensureCodEntryHeader()
        val rows = sheets.spreadsheets().values()
            .get(spreadsheetId, "$COD_ENTRIES_SHEET!A2:G")
            .execute()
            .getValues()
            .orEmpty()

        return rows.mapNotNull { row ->
            val boyId = row.getOrNull(1)?.toString().orEmpty()
            val boyName = row.getOrNull(2)?.toString().orEmpty()
            if (boyName.isBlank()) return@mapNotNull null
            CodDeposit(
                deliveryBoy = DeliveryBoy(boyId, boyName),
                date = runCatching { LocalDate.parse(row.getOrNull(0)?.toString()) }.getOrDefault(LocalDate.now()),
                totalCollected = row.getOrNull(3).amount(),
                cashDeposited = row.getOrNull(4).amount(),
                onlineDeposited = row.getOrNull(5).amount(),
            )
        }
    }

    override fun save(entry: CodDeposit) {
        ensureCodEntryHeader()
        sheets.spreadsheets().values()
            .append(
                spreadsheetId,
                "$COD_ENTRIES_SHEET!A:G",
                ValueRange().setValues(
                    listOf(
                        listOf(
                            entry.date.toString(),
                            entry.deliveryBoy.id,
                            entry.deliveryBoy.name,
                            entry.totalCollected,
                            entry.cashDeposited,
                            entry.onlineDeposited,
                            entry.dueAmount,
                        ),
                    ),
                ),
            )
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    private fun Any?.amount(): Double = this?.toString()?.toDoubleOrNull() ?: 0.0

    private fun ensureDeliveryBoySpreadsheet(): String {
        val folderId = ensureAppFolder()
        val existing = drive.files().list()
            .setQ("name = '$DELIVERY_BOY_FILE' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            .firstOrNull()

        val id = existing?.id ?: sheets.spreadsheets().create(
            Spreadsheet().setProperties(
                com.google.api.services.sheets.v4.model.SpreadsheetProperties()
                    .setTitle(DELIVERY_BOY_FILE),
            ),
        ).execute().spreadsheetId

        drive.files().update(id, null)
            .setAddParents(folderId)
            .setFields("id, parents")
            .execute()
        ensureSheet(id, DELIVERY_BOY_SHEET)
        ensureSheet(id, COD_ENTRIES_SHEET)
        ensureDeliveryBoyHeader(id)
        ensureCodEntryHeader(id)
        return id
    }

    private fun ensureAppFolder(): String {
        val existing = drive.files().list()
            .setQ("name = '$APP_FOLDER' and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            .firstOrNull()
        return existing?.id ?: drive.files().create(
            File()
                .setName(APP_FOLDER)
                .setMimeType("application/vnd.google-apps.folder"),
        ).setFields("id").execute().id
    }

    private fun ensureDeliveryBoyHeader(id: String = spreadsheetId) {
        ensureSheet(id, DELIVERY_BOY_SHEET)
        updateHeader(id, DELIVERY_BOY_SHEET, listOf("id", "name"))
    }

    private fun ensureCodEntryHeader(id: String = spreadsheetId) {
        ensureSheet(id, COD_ENTRIES_SHEET)
        updateHeader(id, COD_ENTRIES_SHEET, listOf("date", "deliveryBoyId", "deliveryBoyName", "total", "cash", "online", "due"))
    }

    private fun ensureSheet(id: String, sheetName: String) {
        val hasSheet = sheets.spreadsheets().get(id)
            .setFields("sheets(properties(title))")
            .execute()
            .sheets
            .orEmpty()
            .any { it.properties.title == sheetName }
        if (hasSheet) return

        sheets.spreadsheets().batchUpdate(
            id,
            BatchUpdateSpreadsheetRequest().setRequests(
                listOf(
                    Request().setAddSheet(
                        AddSheetRequest().setProperties(
                            SheetProperties().setTitle(sheetName),
                        ),
                    ),
                ),
            ),
        ).execute()
    }

    private fun updateHeader(id: String, sheetName: String, header: List<String>) {
        sheets.spreadsheets().values()
            .update(id, "$sheetName!A1", ValueRange().setValues(listOf(header)))
            .setValueInputOption("RAW")
            .execute()
    }

    companion object {
        const val APP_NAME = "COD Calculator"
        const val APP_FOLDER = "XCODCalculator"
        const val DELIVERY_BOY_FILE = "delivery boy"
        const val DELIVERY_BOY_SHEET = "DeliveryBoys"
        const val COD_ENTRIES_SHEET = "CodEntries"
    }
}

class InMemoryGoogleSheetRepository : GoogleSheetRepository {
    private val boys = mutableListOf(
        DeliveryBoy("DB001", "Amit Kumar"),
        DeliveryBoy("DB002", "Rahul Singh"),
    )

    private val deposits = mutableListOf(
        CodDeposit(boys[0], LocalDate.now(), 2500.0, 1000.0, 500.0),
        CodDeposit(boys[1], LocalDate.now(), 1800.0, 800.0, 400.0),
    )

    override fun deliveryBoys(): List<DeliveryBoy> = boys.toList()

    override fun entries(): List<CodDeposit> = deposits.toList()

    override fun save(entry: CodDeposit) {
        deposits.add(entry)
    }
}
