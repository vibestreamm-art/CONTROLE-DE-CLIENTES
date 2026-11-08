package br.com.controleclientes

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import br.com.controleclientes.data.AppDatabase
import br.com.controleclientes.data.Client
import br.com.controleclientes.data.DashboardSummary
import br.com.controleclientes.data.LedgerEntry
import br.com.controleclientes.reminders.ReminderScheduler
import br.com.controleclientes.ui.Palette
import br.com.controleclientes.ui.Ui
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var database: AppDatabase
    private lateinit var reminders: ReminderScheduler
    private var selectedTab = Tab.HOME
    private val currency = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    private enum class Tab { HOME, CLIENTS, FINANCE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = AppDatabase(this)
        reminders = ReminderScheduler(this)
        ReminderScheduler.createChannel(this)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.surface
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        requestNotificationPermissionIfNeeded()
        render()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.background)
        }
        val scroller = ScrollView(this).apply {
            clipToPadding = false
            isFillViewport = false
        }
        val body = Ui.column(this).apply {
            setPadding(Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 18), Ui.dp(this@MainActivity, 24))
        }
        addHeader(body)
        when (selectedTab) {
            Tab.HOME -> buildHome(body)
            Tab.CLIENTS -> buildClients(body)
            Tab.FINANCE -> buildFinance(body)
        }
        scroller.addView(body)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(buildBottomNavigation())
        setContentView(root)
    }

    private fun addHeader(body: LinearLayout) {
        val title = when (selectedTab) {
            Tab.HOME -> "Visão geral"
            Tab.CLIENTS -> "Clientes"
            Tab.FINANCE -> "Financeiro"
        }
        val subtitle = when (selectedTab) {
            Tab.HOME -> "Seu negócio, em um só lugar."
            Tab.CLIENTS -> "Organize seus contatos e vendas."
            Tab.FINANCE -> "Entradas, saídas e valores a receber."
        }
        val header = Ui.row(this, Gravity.CENTER_VERTICAL)
        val titles = Ui.column(this)
        titles.addView(Ui.text(this, "CONTROLE DE CLIENTES", 10f, Palette.blue, true))
        Ui.addSpace(titles, this, 5)
        titles.addView(Ui.text(this, title, 27f, Palette.ink, true))
        Ui.addSpace(titles, this, 5)
        titles.addView(Ui.text(this, subtitle, 13f, Palette.muted))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        val addLabel = when (selectedTab) {
            Tab.HOME -> "+  Venda"
            Tab.CLIENTS -> "+  Cliente"
            Tab.FINANCE -> "+  Lançamento"
        }
        header.addView(
            Ui.button(this, addLabel, primary = true) {
                when (selectedTab) {
                    Tab.HOME -> showEntryForm()
                    Tab.CLIENTS -> showClientForm()
                    Tab.FINANCE -> showEntryForm()
                }
            },
            LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Ui.dp(this@MainActivity, 8) }
        )
        body.addView(header)
        Ui.addSpace(body, this, 18)
    }

    private fun buildBottomNavigation(): LinearLayout {
        val bar = Ui.row(this).apply {
            setBackgroundColor(Palette.surface)
            setPadding(Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 8))
            elevation = Ui.dp(this@MainActivity, 8).toFloat()
        }
        listOf(
            Tab.HOME to "Início",
            Tab.CLIENTS to "Clientes",
            Tab.FINANCE to "Financeiro"
        ).forEach { (tab, label) ->
            val selected = selectedTab == tab
            val item = Ui.text(this, label, 12f, if (selected) Palette.blue else Palette.muted, selected).apply {
                gravity = Gravity.CENTER
                setPadding(Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 10), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 10))
                background = Ui.rounded(this@MainActivity, if (selected) Palette.paleBlue else Palette.surface, null, 12)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedTab = tab
                    render()
                }
            }
            bar.addView(item, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = Ui.dp(this@MainActivity, 4)
                rightMargin = Ui.dp(this@MainActivity, 4)
            })
        }
        return bar
    }

    private fun buildHome(body: LinearLayout) {
        val summary = database.summary(todayStartMillis())
        val balanceCard = Ui.card(this, 18).apply {
            background = Ui.rounded(this@MainActivity, Palette.ink, null, 20)
        }
        balanceCard.addView(Ui.text(this, "SALDO DAS MOVIMENTAÇÕES", 11f, 0xFFCED7E7.toInt(), true))
        Ui.addSpace(balanceCard, this, 9)
        balanceCard.addView(Ui.text(this, formatMoney(summary.balanceCents), 30f, 0xFFFFFFFF.toInt(), true))
        Ui.addSpace(balanceCard, this, 5)
        balanceCard.addView(Ui.text(this, "Entradas recebidas menos saídas registradas", 12f, 0xFFCED7E7.toInt()))
        body.addView(balanceCard, LinearLayout.LayoutParams(-1, -2))
        Ui.addSpace(body, this, 12)

        addMetricRow(
            body,
            "Entradas",
            formatMoney(summary.incomeCents),
            Palette.green,
            "Saídas",
            formatMoney(summary.expenseCents),
            Palette.red
        )
        Ui.addSpace(body, this, 10)
        addMetricRow(
            body,
            "A receber",
            formatMoney(summary.outstandingCents),
            Palette.blue,
            "Em atraso",
            formatMoney(summary.overdueCents),
            Palette.red
        )

        Ui.addSpace(body, this, 22)
        addSectionTitle(body, "Ações rápidas")
        val actions = Ui.row(this)
        actions.addView(Ui.button(this, "+ Nova venda", true) { showEntryForm() }, LinearLayout.LayoutParams(0, -2, 1f))
        Ui.horizontalSpace(actions, this, 8)
        actions.addView(Ui.button(this, "+ Entrada / saída") { showEntryForm() }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(actions)

        Ui.addSpace(body, this, 22)
        addSectionTitle(body, "Cobranças pendentes")
        val openSales = database.getOpenSales()
        if (openSales.isEmpty()) {
            addEmptyCard(body, "Tudo em dia", "Vendas com saldo pendente aparecerão aqui.")
        } else {
            openSales.take(5).forEach { addEntryCard(body, it, showPayment = true) }
            if (openSales.size > 5) {
                body.addView(Ui.button(this, "Ver todos os lançamentos") {
                    selectedTab = Tab.FINANCE
                    render()
                })
            }
        }

        Ui.addSpace(body, this, 22)
        addSectionTitle(body, "Movimentações recentes")
        val latest = database.getEntries(5)
        if (latest.isEmpty()) {
            addEmptyCard(body, "Seu painel está pronto", "Cadastre um cliente e registre sua primeira venda.")
        } else {
            latest.forEach { addEntryCard(body, it, showPayment = it.type == "SALE" && it.outstandingCents > 0) }
        }
    }

    private fun addMetricRow(
        parent: LinearLayout,
        firstLabel: String,
        firstValue: String,
        firstColor: Int,
        secondLabel: String,
        secondValue: String,
        secondColor: Int
    ) {
        val row = Ui.row(this, Gravity.TOP)
        row.addView(metricCard(firstLabel, firstValue, firstColor), LinearLayout.LayoutParams(0, -1, 1f))
        Ui.horizontalSpace(row, this, 10)
        row.addView(metricCard(secondLabel, secondValue, secondColor), LinearLayout.LayoutParams(0, -1, 1f))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun metricCard(label: String, value: String, accent: Int): LinearLayout {
        val card = Ui.card(this, 15)
        val marker = View(this).apply {
            background = Ui.rounded(this@MainActivity, accent, null, 2)
        }
        card.addView(marker, LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 4)))
        Ui.addSpace(card, this, 12)
        card.addView(Ui.text(this, label, 12f, Palette.muted))
        Ui.addSpace(card, this, 5)
        card.addView(Ui.text(this, value, 18f, Palette.ink, true))
        return card
    }

    private fun buildClients(body: LinearLayout) {
        val clients = database.getClients()
        if (clients.isEmpty()) {
            addEmptyCard(body, "Comece pelos seus clientes", "Cadastre um cliente para acompanhar vendas, pagamentos e valores pendentes.")
            body.addView(Ui.button(this, "Cadastrar cliente", true) { showClientForm() })
            return
        }
        body.addView(Ui.text(this, "${clients.size} ${if (clients.size == 1) "cliente ativo" else "clientes ativos"}", 13f, Palette.muted))
        Ui.addSpace(body, this, 12)
        clients.forEach { client ->
            val card = Ui.card(this, 16)
            card.addView(Ui.text(this, client.name, 17f, Palette.ink, true))
            val contact = listOf(client.phone, client.address).filter { it.isNotBlank() }.joinToString(" · ")
            if (contact.isNotBlank()) {
                Ui.addSpace(card, this, 5)
                card.addView(Ui.text(this, contact, 12f, Palette.muted))
            }
            val debt = database.clientDebtCents(client.id)
            Ui.addSpace(card, this, 12)
            val debtRow = Ui.row(this)
            debtRow.addView(Ui.text(this, "Em aberto", 12f, Palette.muted), LinearLayout.LayoutParams(0, -2, 1f))
            debtRow.addView(Ui.text(this, formatMoney(debt), 15f, if (debt > 0) Palette.amber else Palette.green, true))
            card.addView(debtRow)
            Ui.addSpace(card, this, 13)
            val buttons = Ui.row(this)
            buttons.addView(Ui.button(this, "Nova venda", true) { showEntryForm(client) }, LinearLayout.LayoutParams(0, -2, 1f))
            Ui.horizontalSpace(buttons, this, 8)
            buttons.addView(Ui.button(this, "Ver detalhes") { showClientDetails(client) }, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(buttons)
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@MainActivity, 10) })
        }
    }

    private fun buildFinance(body: LinearLayout) {
        val summary = database.summary(todayStartMillis())
        val top = Ui.card(this, 16)
        top.addView(Ui.text(this, "Saldo atual", 12f, Palette.muted))
        Ui.addSpace(top, this, 5)
        top.addView(Ui.text(this, formatMoney(summary.balanceCents), 25f, Palette.ink, true))
        Ui.addSpace(top, this, 12)
        val description = "${formatMoney(summary.incomeCents)} recebidos · ${formatMoney(summary.expenseCents)} em saídas"
        top.addView(Ui.text(this, description, 12f, Palette.muted))
        body.addView(top)
        Ui.addSpace(body, this, 20)
        addSectionTitle(body, "Todos os lançamentos")
        val entries = database.getEntries()
        if (entries.isEmpty()) {
            addEmptyCard(body, "Nenhum lançamento ainda", "Registre uma venda, entrada ou saída para acompanhar seu financeiro.")
            return
        }
        entries.forEach { addEntryCard(body, it, showPayment = it.type == "SALE" && it.outstandingCents > 0) }
    }

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(Ui.text(this, title, 17f, Palette.ink, true))
        Ui.addSpace(parent, this, 10)
    }

    private fun addEmptyCard(parent: LinearLayout, title: String, subtitle: String) {
        val card = Ui.card(this, 18)
        card.addView(Ui.text(this, title, 15f, Palette.ink, true))
        Ui.addSpace(card, this, 6)
        card.addView(Ui.text(this, subtitle, 13f, Palette.muted))
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = Ui.dp(this@MainActivity, 8) })
    }

    private fun addEntryCard(parent: LinearLayout, entry: LedgerEntry, showPayment: Boolean) {
        val card = Ui.card(this, 15)
        val topRow = Ui.row(this)
        val (typeLabel, typeColor, typeBackground) = when (entry.type) {
            "SALE" -> Triple("VENDA", Palette.blue, Palette.paleBlue)
            "INCOME" -> Triple("ENTRADA", Palette.green, Palette.paleGreen)
            else -> Triple("SAÍDA", Palette.red, Palette.paleRed)
        }
        val badge = Ui.text(this, typeLabel, 10f, typeColor, true).apply {
            gravity = Gravity.CENTER
            setPadding(Ui.dp(this@MainActivity, 9), Ui.dp(this@MainActivity, 6), Ui.dp(this@MainActivity, 9), Ui.dp(this@MainActivity, 6))
            background = Ui.rounded(this@MainActivity, typeBackground, null, 20)
        }
        topRow.addView(badge)
        Ui.horizontalSpace(topRow, this, 8)
        val customer = entry.clientId?.let { clientName(it) }
        topRow.addView(
            Ui.text(this, customer ?: formatDate(entry.dateMillis), 11f, Palette.muted),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        val signedAmount = if (entry.type == "EXPENSE") "−${formatMoney(entry.amountCents)}" else formatMoney(entry.amountCents)
        topRow.addView(
            Ui.text(this, signedAmount, 13f, if (entry.type == "EXPENSE") Palette.red else Palette.ink, true)
        )
        card.addView(topRow)
        Ui.addSpace(card, this, 9)
        card.addView(Ui.text(this, entry.title, 15f, Palette.ink, true))
        if (entry.type == "SALE") {
            Ui.addSpace(card, this, 5)
            val dueText = entry.dueDateMillis?.let { " · vence ${formatDate(it)}" } ?: ""
            val status = if (entry.outstandingCents > 0) {
                "Pago ${formatMoney(entry.paidCents)} · falta ${formatMoney(entry.outstandingCents)}$dueText"
            } else {
                "Quitada"
            }
            val isOverdue = entry.outstandingCents > 0 &&
                entry.dueDateMillis != null && entry.dueDateMillis < todayStartMillis()
            card.addView(Ui.text(this, status, 12f, if (isOverdue) Palette.red else Palette.muted))
        } else if (entry.notes.isNotBlank()) {
            Ui.addSpace(card, this, 4)
            card.addView(Ui.text(this, entry.notes, 12f, Palette.muted))
        }
        if (showPayment) {
            Ui.addSpace(card, this, 11)
            card.addView(Ui.button(this, "Registrar pagamento") { showPaymentDialog(entry) })
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = Ui.dp(this@MainActivity, 9)
        })
    }

    private fun showClientForm(client: Client? = null) {
        val form = Ui.column(this)
        val name = inputField(form, "Nome *", client?.name.orEmpty(), "Nome do cliente")
        val phone = inputField(form, "Telefone", client?.phone.orEmpty(), "(00) 00000-0000", InputType.TYPE_CLASS_PHONE)
        val address = inputField(form, "Endereço", client?.address.orEmpty(), "Rua, número, bairro")
        val notes = inputField(form, "Observações", client?.notes.orEmpty(), "Informações úteis", multiline = true)
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (client == null) "Novo cliente" else "Editar cliente")
            .setView(formInScroll(form))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cleanedName = name.text.toString().trim()
                if (cleanedName.isBlank()) {
                    name.error = "Informe o nome do cliente."
                    return@setOnClickListener
                }
                database.saveClient(
                    Client(
                        id = client?.id ?: 0L,
                        name = cleanedName,
                        phone = phone.text.toString(),
                        address = address.text.toString(),
                        notes = notes.text.toString(),
                        active = true
                    )
                )
                dialog.dismiss()
                Toast.makeText(this, if (client == null) "Cliente cadastrado." else "Cliente atualizado.", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        dialog.show()
    }

    private fun showClientDetails(client: Client) {
        val content = Ui.column(this)
        content.addView(Ui.text(this, client.name, 21f, Palette.ink, true))
        if (client.phone.isNotBlank()) {
            Ui.addSpace(content, this, 6)
            content.addView(Ui.text(this, client.phone, 14f, Palette.muted))
        }
        if (client.address.isNotBlank()) {
            Ui.addSpace(content, this, 4)
            content.addView(Ui.text(this, client.address, 13f, Palette.muted))
        }
        Ui.addSpace(content, this, 12)
        content.addView(Ui.text(this, "Em aberto: ${formatMoney(database.clientDebtCents(client.id))}", 15f, Palette.amber, true))
        if (client.notes.isNotBlank()) {
            Ui.addSpace(content, this, 8)
            content.addView(Ui.text(this, client.notes, 13f, Palette.muted))
        }
        Ui.addSpace(content, this, 16)
        content.addView(Ui.text(this, "Histórico", 16f, Palette.ink, true))
        val clientEntries = database.getEntries().filter { it.clientId == client.id }
        if (clientEntries.isEmpty()) {
            Ui.addSpace(content, this, 7)
            content.addView(Ui.text(this, "Nenhuma movimentação registrada.", 13f, Palette.muted))
        } else {
            clientEntries.forEach { addEntryCard(content, it, showPayment = false) }
            clientEntries.filter { it.type == "SALE" && it.outstandingCents > 0 }.forEach { entry ->
                Ui.addSpace(content, this, 4)
                content.addView(Ui.button(this, "Receber ${formatMoney(entry.outstandingCents)}") {
                    showPaymentDialog(entry)
                })
                Ui.addSpace(content, this, 9)
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Detalhes do cliente")
            .setView(formInScroll(content))
            .setNegativeButton("Arquivar", null)
            .setNeutralButton("Editar", null)
            .setPositiveButton("Nova venda", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                showClientForm(client)
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                showEntryForm(client)
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                confirmArchive(client) { dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun confirmArchive(client: Client, onArchived: () -> Unit = {}) {
        AlertDialog.Builder(this)
            .setTitle("Arquivar cliente?")
            .setMessage("O histórico financeiro será preservado. O cliente deixará de aparecer na lista ativa.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Arquivar") { _, _ ->
                database.archiveClient(client.id)
                onArchived()
                render()
                Toast.makeText(this, "Cliente arquivado.", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showEntryForm(prefilledClient: Client? = null) {
        val clients = database.getClients()
        val form = Ui.column(this)
        form.addView(Ui.text(this, "Tipo de lançamento", 12f, Palette.muted, true))
        Ui.addSpace(form, this, 5)
        val types = listOf("Venda", "Entrada", "Saída")
        val typeSpinner = spinner(types)
        form.addView(typeSpinner)
        Ui.addSpace(form, this, 11)
        val title = inputField(form, "Descrição *", "", "Ex.: Compra de material")
        val amount = inputField(form, "Valor total *", "", "0,00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val paid = inputField(form, "Valor já recebido", "0,00", "0,00", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        form.addView(Ui.text(this, "Cliente", 12f, Palette.muted, true))
        Ui.addSpace(form, this, 5)
        val clientOptions = listOf("Sem cliente") + clients.map { it.name }
        val clientSpinner = spinner(clientOptions)
        if (prefilledClient != null) clientSpinner.setSelection(clients.indexOfFirst { it.id == prefilledClient.id } + 1)
        form.addView(clientSpinner)
        Ui.addSpace(form, this, 11)
        val dueDate = inputField(form, "Vencimento (opcional)", "", "dd/mm/aaaa", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        val notes = inputField(form, "Observações", "", "Detalhes opcionais", multiline = true)

        fun updateTypeFields() {
            val isSale = typeSpinner.selectedItemPosition == 0
            (paid.parent as? View)?.visibility = if (isSale) View.VISIBLE else View.GONE
            (dueDate.parent as? View)?.let { wrapper ->
                // The date input and its label share the wrapper created by inputField.
                wrapper.visibility = if (isSale) View.VISIBLE else View.GONE
            }
            clientSpinner.visibility = View.VISIBLE
            (clientSpinner.parent as? LinearLayout)?.let { wrapper ->
                wrapper.visibility = View.VISIBLE
            }
        }
        typeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateTypeFields()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Novo lançamento")
            .setView(formInScroll(form))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = title.text.toString().trim()
                val totalCents = parseMoney(amount.text.toString())
                if (label.isBlank()) {
                    title.error = "Informe uma descrição."
                    return@setOnClickListener
                }
                if (totalCents == null || totalCents <= 0) {
                    amount.error = "Informe um valor válido maior que zero."
                    return@setOnClickListener
                }
                val selectedClient = clientSpinner.selectedItemPosition - 1
                val clientId = clients.getOrNull(selectedClient)?.id
                val noteText = notes.text.toString().trim()
                when (typeSpinner.selectedItemPosition) {
                    0 -> {
                        val paidCents = parseMoney(paid.text.toString()) ?: 0L
                        if (paidCents > totalCents) {
                            paid.error = "O valor recebido não pode superar o valor da venda."
                            return@setOnClickListener
                        }
                        val parsedDueDate = parseDateOrNull(dueDate.text.toString())
                        if (dueDate.text.toString().isNotBlank() && parsedDueDate == null) {
                            dueDate.error = "Use o formato dia/mês/ano."
                            return@setOnClickListener
                        }
                        val saleId = database.addSale(label, clientId, totalCents, paidCents, parsedDueDate, noteText)
                        database.getEntry(saleId)?.let { reminders.schedule(it) }
                    }
                    1 -> database.addCashEntry("INCOME", label, clientId, totalCents, noteText)
                    else -> database.addCashEntry("EXPENSE", label, clientId, totalCents, noteText)
                }
                dialog.dismiss()
                Toast.makeText(this, "Lançamento salvo.", Toast.LENGTH_SHORT).show()
                render()
            }
        }
        dialog.show()
    }

    private fun showPaymentDialog(sale: LedgerEntry) {
        val amount = inputField(
            Ui.column(this),
            "Valor recebido *",
            "",
            "Saldo: ${formatMoney(sale.outstandingCents)}",
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        )
        val form = amount.parent?.parent as? LinearLayout
        val container = form ?: Ui.column(this).apply { addView(amount) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Registrar pagamento")
            .setMessage(sale.title)
            .setView(formInScroll(container))
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Confirmar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = parseMoney(amount.text.toString())
                if (cents == null || cents <= 0 || cents > sale.outstandingCents) {
                    amount.error = "Informe até ${formatMoney(sale.outstandingCents)}."
                    return@setOnClickListener
                }
                try {
                    database.registerPayment(sale.id, cents)
                    val updated = database.getEntry(sale.id)
                    if (updated?.outstandingCents == 0L) reminders.cancel(sale.id)
                    dialog.dismiss()
                    Toast.makeText(this, "Pagamento registrado.", Toast.LENGTH_SHORT).show()
                    render()
                } catch (error: IllegalArgumentException) {
                    amount.error = error.message
                }
            }
        }
        dialog.show()
    }

    private fun inputField(
        parent: LinearLayout,
        label: String,
        value: String,
        hint: String,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        multiline: Boolean = false
    ): EditText {
        val wrapper = Ui.column(this)
        wrapper.addView(Ui.text(this, label, 12f, Palette.muted, true))
        Ui.addSpace(wrapper, this, 5)
        val edit = EditText(this).apply {
            setText(value)
            this.hint = hint
            this.inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else inputType
            textSize = 14f
            setTextColor(Palette.ink)
            setHintTextColor(Palette.muted)
            setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 11), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 11))
            background = Ui.rounded(this@MainActivity, Palette.surface, Palette.line, 12)
            if (multiline) {
                minLines = 2
                gravity = Gravity.TOP or Gravity.START
            }
        }
        wrapper.addView(edit, LinearLayout.LayoutParams(-1, -2))
        parent.addView(wrapper, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = Ui.dp(this@MainActivity, 10)
        })
        return edit
    }

    private fun spinner(options: List<String>): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        background = Ui.rounded(this@MainActivity, Palette.surface, Palette.line, 12)
        setPadding(Ui.dp(this@MainActivity, 8), 0, Ui.dp(this@MainActivity, 8), 0)
    }

    private fun formInScroll(view: View): ScrollView = ScrollView(this).apply {
        setPadding(Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 20), Ui.dp(this@MainActivity, 4))
        isFillViewport = false
        addView(view)
    }

    private fun clientName(id: Long): String =
        database.getClients(includeArchived = true).firstOrNull { it.id == id }?.name ?: "Cliente arquivado"

    private fun formatMoney(cents: Long): String = currency.format(cents / 100.0)

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

    private fun parseDateOrNull(text: String): Long? = try {
        if (text.isBlank()) null
        else LocalDate.parse(text.trim(), dateFormatter)
            .atTime(23, 59, 59)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        null
    }

    private fun parseMoney(input: String): Long? {
        val value = input.trim().replace("R$", "", ignoreCase = true).replace(" ", "")
        if (value.isBlank()) return null
        val normalized = when {
            value.contains(',') && value.contains('.') -> value.replace(".", "").replace(',', '.')
            value.contains(',') -> value.replace(',', '.')
            else -> value
        }
        return try {
            BigDecimal(normalized)
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    private fun todayStartMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
