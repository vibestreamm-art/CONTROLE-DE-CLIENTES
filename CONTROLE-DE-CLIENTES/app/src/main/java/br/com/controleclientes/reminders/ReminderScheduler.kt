package br.com.controleclientes.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import br.com.controleclientes.MainActivity
import br.com.controleclientes.data.AppDatabase
import br.com.controleclientes.data.LedgerEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(entry: LedgerEntry) {
        val dueMillis = entry.dueDateMillis ?: return
        if (entry.type != "SALE" || entry.outstandingCents <= 0) return
        val dueDay = Instant.ofEpochMilli(dueMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val triggerAt = dueDay.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            .coerceAtLeast(System.currentTimeMillis() + 60_000)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            reminderPendingIntent(context, entry.id)
        )
    }

    fun cancel(entryId: Long) {
        alarmManager.cancel(reminderPendingIntent(context, entryId))
    }

    companion object {
        const val CHANNEL_ID = "cobrancas_vencimento"
        const val EXTRA_ENTRY_ID = "entry_id"

        internal fun reminderPendingIntent(context: Context, entryId: Long): PendingIntent {
            val intent = Intent(context, DueReminderReceiver::class.java).apply {
                data = Uri.parse("controleclientes://lembrete/$entryId")
                putExtra(EXTRA_ENTRY_ID, entryId)
            }
            return PendingIntent.getBroadcast(
                context,
                entryId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Vencimentos e cobranças",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Lembretes locais para vendas com saldo a receber."
                    }
                )
            }
        }

        fun showReminder(context: Context, entry: LedgerEntry) {
            if (entry.outstandingCents <= 0) return
            createChannel(context)
            val openApp = PendingIntent.getActivity(
                context,
                entry.id.toInt(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val amount = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("pt", "BR"))
                .format(entry.outstandingCents / 100.0)
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(context, CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Lembrete de cobrança")
                .setContentText("${entry.title}: $amount a receber")
                .setStyle(Notification.BigTextStyle().bigText("${entry.title} tem $amount pendente. Abra o Controle de Clientes para registrar o pagamento."))
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            try {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                    .notify(entry.id.toInt(), notification)
            } catch (_: SecurityException) {
                // Android 13+ may deny notifications; the outstanding debt remains visible in the app.
            }
        }
    }
}

class DueReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ENTRY_ID, -1L)
        if (id <= 0) return
        AppDatabase(context).use { database ->
            database.getEntry(id)?.let { ReminderScheduler.showReminder(context, it) }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val scheduler = ReminderScheduler(context)
        AppDatabase(context).use { database ->
            database.getOpenSales().forEach { sale ->
                if (sale.dueDateMillis != null) {
                    val dueDate = Instant.ofEpochMilli(sale.dueDateMillis)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    if (!dueDate.isAfter(LocalDate.now())) {
                        ReminderScheduler.showReminder(context, sale)
                    } else {
                        scheduler.schedule(sale)
                    }
                }
            }
        }
    }
}
