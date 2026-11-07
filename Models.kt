package br.com.controleclientes.data

data class Client(
    val id: Long,
    val name: String,
    val phone: String,
    val address: String,
    val notes: String,
    val active: Boolean
)

data class LedgerEntry(
    val id: Long,
    val type: String,
    val title: String,
    val clientId: Long?,
    val amountCents: Long,
    val dateMillis: Long,
    val dueDateMillis: Long?,
    val paidCents: Long,
    val notes: String
) {
    val outstandingCents: Long
        get() = (amountCents - paidCents).coerceAtLeast(0)
}

data class DashboardSummary(
    val incomeCents: Long,
    val expenseCents: Long,
    val balanceCents: Long,
    val outstandingCents: Long,
    val overdueCents: Long
)
