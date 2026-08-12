package com.example.codcalculator

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat
import java.time.LocalDate

class MainActivity : Activity() {
    private val repository: GoogleSheetRepository = InMemoryGoogleSheetRepository()
    private val money = NumberFormat.getCurrencyInstance()

    private lateinit var deliveryBoySpinner: Spinner
    private lateinit var totalInput: EditText
    private lateinit var cashInput: EditText
    private lateinit var onlineInput: EditText
    private lateinit var dueValue: TextView
    private lateinit var dueList: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
        refreshDueAmount()
        refreshDueList()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(header("COD deposit entry"))
        deliveryBoySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                repository.deliveryBoys().map { it.name },
            )
        }
        root.addView(label("Delivery boy (from Google Sheet)"))
        root.addView(deliveryBoySpinner)

        totalInput = numberInput("Total COD collected")
        cashInput = numberInput("Cash deposited")
        onlineInput = numberInput("Online deposited")
        listOf(totalInput, cashInput, onlineInput).forEach { input ->
            root.addView(input)
            input.setOnFocusChangeListener { _, _ -> refreshDueAmount() }
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

    private fun saveEntry() {
        refreshDueAmount()
        val selectedBoy = repository.deliveryBoys()[deliveryBoySpinner.selectedItemPosition]
        val entry = CodDeposit(
            deliveryBoy = selectedBoy,
            date = LocalDate.now(),
            totalCollected = totalInput.amount(),
            cashDeposited = cashInput.amount(),
            onlineDeposited = onlineInput.amount(),
        )
        repository.save(entry)
        Toast.makeText(this, "Entry saved for ${entry.date}", Toast.LENGTH_SHORT).show()
        refreshDueList()
    }

    private fun refreshDueAmount() {
        if (!::dueValue.isInitialized) return
        val due = totalInput.amount() - cashInput.amount() - onlineInput.amount()
        dueValue.text = "Due: ${money.format(due)}"
    }

    private fun refreshDueList() {
        val dues = repository.entries()
            .groupBy { it.deliveryBoy.name }
            .mapValues { (_, entries) -> entries.sumOf { it.dueAmount } }
            .entries
            .joinToString(separator = "\n") { (name, due) -> "$name: ${money.format(due)}" }
        dueList.text = dues.ifBlank { "No due amount found." }
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
}
