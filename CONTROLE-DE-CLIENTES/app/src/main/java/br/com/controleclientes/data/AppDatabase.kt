package br.com.controleclientes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE clients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                address TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL CHECK(type IN ('SALE', 'INCOME', 'EXPENSE')),
                title TEXT NOT NULL,
                client_id INTEGER,
                amount_cents INTEGER NOT NULL CHECK(amount_cents >= 0),
                date_millis INTEGER NOT NULL,
                due_date_millis INTEGER,
                paid_cents INTEGER NOT NULL DEFAULT 0 CHECK(paid_cents >= 0),
                notes TEXT NOT NULL DEFAULT '',
                FOREIGN KEY(client_id) REFERENCES clients(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_entries_type_date ON entries(type, date_millis DESC)")
        db.execSQL("CREATE INDEX index_entries_client ON entries(client_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Increment the schema version and add migrations here before changing existing tables.
    }

    fun getClients(includeArchived: Boolean = false): List<Client> {
        val clients = mutableListOf<Client>()
        val where = if (includeArchived) null else "active = 1"
        readableDatabase.query(
            "clients",
            arrayOf("id", "name", "phone", "address", "notes", "active"),
            where,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                clients += Client(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    phone = cursor.getString(2),
                    address = cursor.getString(3),
                    notes = cursor.getString(4),
                    active = cursor.getInt(5) == 1
                )
            }
        }
        return clients
    }

    fun saveClient(client: Client?): Long {
        val savedClient = requireNotNull(client)
        val values = ContentValues().apply {
            put("name", savedClient.name.trim())
            put("phone", savedClient.phone.trim())
            put("address", savedClient.address.trim())
            put("notes", savedClient.notes.trim())
        }
        return if (savedClient.id == 0L) {
            values.put("active", 1)
            values.put("created_at", System.currentTimeMillis())
            writableDatabase.insertOrThrow("clients", null, values)
        } else {
            writableDatabase.update("clients", values, "id = ?", arrayOf(savedClient.id.toString()))
            savedClient.id
        }
    }

    fun archiveClient(clientId: Long) {
        val values = ContentValues().apply { put("active", 0) }
        writableDatabase.update("clients", values, "id = ?", arrayOf(clientId.toString()))
    }

    fun getEntries(limit: Int? = null): List<LedgerEntry> {
        val entries = mutableListOf<LedgerEntry>()
        readableDatabase.query(
            "entries",
            ENTRY_COLUMNS,
            null,
            null,
            null,
            null,
            "date_millis DESC, id DESC",
            limit?.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) entries += cursor.toEntry()
        }
        return entries
    }

    fun getEntry(id: Long): LedgerEntry? {
        readableDatabase.query(
            "entries",
            ENTRY_COLUMNS,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.toEntry()
        }
        return null
    }

    fun getOpenSales(clientId: Long? = null): List<LedgerEntry> {
        val selection = buildString {
            append("type = 'SALE' AND amount_cents > paid_cents")
            if (clientId != null) append(" AND client_id = ").append(clientId)
        }
        val entries = mutableListOf<LedgerEntry>()
        readableDatabase.query(
            "entries",
            ENTRY_COLUMNS,
            selection,
            null,
            null,
            null,
            "COALESCE(due_date_millis, date_millis) ASC, date_millis ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) entries += cursor.toEntry()
        }
        return entries
    }

    fun addSale(
        title: String,
        clientId: Long?,
        amountCents: Long,
        paidCents: Long,
        dueDateMillis: Long?,
        notes: String
    ): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sale = ContentValues().apply {
                put("type", "SALE")
                put("title", title.trim())
                if (clientId == null) putNull("client_id") else put("client_id", clientId)
                put("amount_cents", amountCents)
                put("date_millis", System.currentTimeMillis())
                if (dueDateMillis == null) putNull("due_date_millis") else put("due_date_millis", dueDateMillis)
                put("paid_cents", paidCents)
                put("notes", notes.trim())
            }
            val saleId = db.insertOrThrow("entries", null, sale)
            if (paidCents > 0) {
                val payment = ContentValues().apply {
                    put("type", "INCOME")
                    put("title", "Pagamento: ${title.trim()}")
                    if (clientId == null) putNull("client_id") else put("client_id", clientId)
                    put("amount_cents", paidCents)
                    put("date_millis", System.currentTimeMillis())
                    putNull("due_date_millis")
                    put("paid_cents", 0)
                    put("notes", "Pagamento inicial da venda #$saleId")
                }
                db.insertOrThrow("entries", null, payment)
            }
            db.setTransactionSuccessful()
            return saleId
        } finally {
            db.endTransaction()
        }
    }

    fun addCashEntry(type: String, title: String, clientId: Long?, amountCents: Long, notes: String) {
        require(type == "INCOME" || type == "EXPENSE")
        val values = ContentValues().apply {
            put("type", type)
            put("title", title.trim())
            if (clientId == null) putNull("client_id") else put("client_id", clientId)
            put("amount_cents", amountCents)
            put("date_millis", System.currentTimeMillis())
            putNull("due_date_millis")
            put("paid_cents", 0)
            put("notes", notes.trim())
        }
        writableDatabase.insertOrThrow("entries", null, values)
    }

    fun registerPayment(saleId: Long, amountCents: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sale = getEntry(saleId) ?: throw IllegalArgumentException("Venda não encontrada.")
            require(sale.type == "SALE") { "Este lançamento não é uma venda." }
            require(amountCents in 1..sale.outstandingCents) { "O pagamento deve ser maior que zero e não pode exceder o saldo." }

            val update = ContentValues().apply { put("paid_cents", sale.paidCents + amountCents) }
            db.update("entries", update, "id = ?", arrayOf(saleId.toString()))

            val payment = ContentValues().apply {
                put("type", "INCOME")
                put("title", "Pagamento: ${sale.title}")
                if (sale.clientId == null) putNull("client_id") else put("client_id", sale.clientId)
                put("amount_cents", amountCents)
                put("date_millis", System.currentTimeMillis())
                putNull("due_date_millis")
                put("paid_cents", 0)
                put("notes", "Pagamento da venda #$saleId")
            }
            db.insertOrThrow("entries", null, payment)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun summary(todayStartMillis: Long): DashboardSummary {
        var income = 0L
        var expense = 0L
        readableDatabase.rawQuery(
            "SELECT type, SUM(amount_cents) FROM entries WHERE type IN ('INCOME', 'EXPENSE') GROUP BY type",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                when (cursor.getString(0)) {
                    "INCOME" -> income = cursor.getLong(1)
                    "EXPENSE" -> expense = cursor.getLong(1)
                }
            }
        }
        var outstanding = 0L
        var overdue = 0L
        readableDatabase.rawQuery(
            """
            SELECT COALESCE(SUM(amount_cents - paid_cents), 0),
                   COALESCE(SUM(CASE WHEN due_date_millis IS NOT NULL AND due_date_millis < ? THEN amount_cents - paid_cents ELSE 0 END), 0)
            FROM entries
            WHERE type = 'SALE' AND amount_cents > paid_cents
            """.trimIndent(),
            arrayOf(todayStartMillis.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                outstanding = cursor.getLong(0)
                overdue = cursor.getLong(1)
            }
        }
        return DashboardSummary(income, expense, income - expense, outstanding, overdue)
    }

    fun clientDebtCents(clientId: Long): Long {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(amount_cents - paid_cents), 0) FROM entries WHERE type = 'SALE' AND client_id = ? AND amount_cents > paid_cents",
            arrayOf(clientId.toString())
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun android.database.Cursor.toEntry(): LedgerEntry = LedgerEntry(
        id = getLong(0),
        type = getString(1),
        title = getString(2),
        clientId = if (isNull(3)) null else getLong(3),
        amountCents = getLong(4),
        dateMillis = getLong(5),
        dueDateMillis = if (isNull(6)) null else getLong(6),
        paidCents = getLong(7),
        notes = getString(8)
    )

    companion object {
        private const val DATABASE_NAME = "controle_clientes.db"
        private const val DATABASE_VERSION = 1
        private val ENTRY_COLUMNS = arrayOf(
            "id", "type", "title", "client_id", "amount_cents",
            "date_millis", "due_date_millis", "paid_cents", "notes"
        )
    }
}
