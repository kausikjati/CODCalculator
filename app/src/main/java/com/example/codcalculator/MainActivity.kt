package com.example.codcalculator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var repository: GoogleSheetRepository = InMemoryGoogleSheetRepository()
    private val money = NumberFormat.getCurrencyInstance()
    private var deliveryBoys = emptyList<DeliveryBoy>()

    private lateinit var deliveryBoyAdapter: ArrayAdapter<String>
    private lateinit var deliveryBoySpinner: Spinner
    private lateinit var totalInput: EditText
    private lateinit var cashInput: EditText
    private lateinit var onlineInput: EditText
    private lateinit var dueValue: TextView
    private lateinit var dueList: TextView
    private lateinit var syncStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        loadDeliveryBoys()
        refreshDueAmount()
        refreshDueList()
        requestGoogleDriveAccess()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GOOGLE_SIGN_IN_REQUEST) return

        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { account ->
                syncStatus.text = "Google Drive connected. Creating XCODCalculator/delivery boy..."
                repository = DriveBackedGoogleSheetRepository(this, account)
                loadDeliveryBoysFromDrive()
            }
            .addOnFailureListener { error ->
                syncStatus.text = "Google Drive access was not granted. Using local sample data."
                Toast.makeText(this, error.localizedMessage ?: "Google sign-in failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(header("COD deposit entry"))
        syncStatus = label("Requesting Google Drive access...")
        root.addView(syncStatus)

        deliveryBoyAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mutableListOf())
        deliveryBoySpinner = Spinner(this).apply { adapter = deliveryBoyAdapter }
        root.addView(label("Delivery boy (from Google Drive file: delivery boy)"))
        root.addView(deliveryBoySpinner)

        totalInput = numberInput("Total COD collected")
        cashInput = numberInput("Cash deposited")
        onlineInput = numberInput("Online deposited")
        listOf(totalInput, cashInput, onlineInput).forEach { input ->
            root.addView(input)
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshDueAmount()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        dueValue = header("Due: ${money.format(0)}")
        root.addView(dueValue)

        root.addView(Button(this).apply {
            text = "Save & sync to Google Sheet"
            setOnClickListener { saveEntry() }
        })

        root.addView(header("Delivery boy due amounts"))
        dueList = TextView(this).apply { textSize = 16f }
        root.addView(dueList)
        return root
    }

    private fun requestGoogleDriveAccess() {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(SheetsScopes.SPREADSHEETS),
            )
            .build()
        startActivityForResult(GoogleSignIn.getClient(this, options).signInIntent, GOOGLE_SIGN_IN_REQUEST)
    }

    private fun loadDeliveryBoysFromDrive() {
        thread {
            runCatching { repository.deliveryBoys() to buildDueListText() }
                .onSuccess { (boys, duesText) ->
                    runOnUiThread {
                        if (boys.isEmpty()) {
                            syncStatus.text = "Created XCODCalculator/delivery boy. Add delivery boys to the DeliveryBoys sheet."
                        } else {
                            syncStatus.text = "Loaded delivery boys from XCODCalculator/delivery boy."
                        }
                        setDeliveryBoys(boys)
                        dueList.text = duesText
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        syncStatus.text = "Could not sync Google Sheet. Using local sample data."
                        Toast.makeText(this, error.localizedMessage ?: "Google Sheet sync failed", Toast.LENGTH_LONG).show()
                        repository = InMemoryGoogleSheetRepository()
                        loadDeliveryBoys()
                    }
                }
        }
    }

    private fun loadDeliveryBoys() {
        setDeliveryBoys(repository.deliveryBoys())
    }

    private fun setDeliveryBoys(boys: List<DeliveryBoy>) {
        deliveryBoys = boys
        deliveryBoyAdapter.clear()
        deliveryBoyAdapter.addAll(boys.map { it.name })
        deliveryBoyAdapter.notifyDataSetChanged()
    }

    private fun saveEntry() {
        if (deliveryBoys.isEmpty()) {
            Toast.makeText(this, "Add a delivery boy in Google Sheet first.", Toast.LENGTH_SHORT).show()
            return
        }
        refreshDueAmount()
        val selectedBoy = deliveryBoys[deliveryBoySpinner.selectedItemPosition]
        val entry = CodDeposit(
            deliveryBoy = selectedBoy,
            date = LocalDate.now(),
            totalCollected = totalInput.amount(),
            cashDeposited = cashInput.amount(),
            onlineDeposited = onlineInput.amount(),
        )
        thread {
            runCatching { repository.save(entry) }
                .onSuccess {
                    val duesText = buildDueListText()
                    runOnUiThread {
                        Toast.makeText(this, "Entry saved for ${entry.date}", Toast.LENGTH_SHORT).show()
                        dueList.text = duesText
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        Toast.makeText(this, error.localizedMessage ?: "Save failed", Toast.LENGTH_LONG).show()
                    }
                }
        }
    }

    private fun refreshDueAmount() {
        if (!::dueValue.isInitialized) return
        val due = totalInput.amount() - cashInput.amount() - onlineInput.amount()
        dueValue.text = "Due: ${money.format(due)}"
    }

    private fun refreshDueList() {
        dueList.text = buildDueListText()
    }

    private fun buildDueListText(): String {
        val dues = repository.entries()
            .groupBy { it.deliveryBoy.name }
            .mapValues { (_, entries) -> entries.sumOf { it.dueAmount } }
            .entries
            .joinToString(separator = "\n") { (name, due) -> "$name: ${money.format(due)}" }
        return dues.ifBlank { "No due amount found." }
    }

    private fun numberInput(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun EditText.amount(): Double = text.toString().toDoubleOrNull() ?: 0.0

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 20f
        setPadding(0, 24, 0, 12)
    }

    companion object {
        private const val GOOGLE_SIGN_IN_REQUEST = 1001
    }
}
