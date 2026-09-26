package com.shs.calendar.ui.caldav

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.shs.calendar.R
import com.shs.calendar.data.entity.SyncAccountEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Renders [SyncAccountEntity] rows for the sync-accounts screen.
 *
 * The status line is the substance of this row. It reports the last real
 * outcome — error text when the last pass failed, otherwise when it last
 * succeeded, otherwise "not synced yet" — so a silently stopped sync is
 * visible on the screen that owns the account. Deliberately never guesses
 * from connectivity: a reachable server and a working account are different
 * facts, and only the sync result settles the second one.
 */
class CalDavAccountsAdapter(
    private val onToggle: (SyncAccountEntity, Boolean) -> Unit
) : RecyclerView.Adapter<CalDavAccountsAdapter.Holder>() {

    private var rows: List<SyncAccountEntity> = emptyList()

    fun submit(accounts: List<SyncAccountEntity>) {
        rows = accounts
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): SyncAccountEntity? = rows.getOrNull(position)

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.caldav_account_label)
        val server: TextView = view.findViewById(R.id.caldav_account_server)
        val status: TextView = view.findViewById(R.id.caldav_account_status)
        val color: View = view.findViewById(R.id.caldav_account_color)
        val enabled: SwitchMaterial = view.findViewById(R.id.caldav_account_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_caldav_account, parent, false))

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val account = rows[position]
        val context = holder.itemView.context

        holder.label.text = account.label
        holder.server.text = account.serverUrl

        holder.color.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8f
            setColor(accentFor(account.id))
        }

        // Disabled accounts get one honest line rather than a stale "last
        // synced" from before they were switched off, which would imply the
        // account is still keeping up with the server.
        if (!account.enabled) {
            holder.status.setTextColor(context.getColor(R.color.shs_text_muted))
            holder.status.text = context.getString(R.string.caldav_disabled)
        } else {
            val (text, colorRes) = when {
                account.lastErrorMessage.isNotBlank() ->
                    context.getString(R.string.caldav_sync_error, account.lastErrorMessage) to R.color.shs_danger
                account.lastSyncMillis > 0L ->
                    context.getString(R.string.caldav_last_synced, formatWhen(account.lastSyncMillis)) to R.color.shs_text_muted
                else ->
                    context.getString(R.string.caldav_never_synced) to R.color.shs_text_muted
            }
            holder.status.text = text
            holder.status.setTextColor(context.getColor(colorRes))
        }

        // Detach the listener before setting state, or recycling a row whose
        // switch is already checked fires setOnCheckedChangeListener and
        // writes the *previous* account's value onto the new one.
        holder.enabled.setOnCheckedChangeListener(null)
        holder.enabled.isChecked = account.enabled
        holder.enabled.setOnCheckedChangeListener { _, checked ->
            val current = itemAt(holder.bindingAdapterPosition)
            if (current != null && current.enabled != checked) onToggle(current, checked)
        }

        holder.itemView.alpha = if (account.enabled) 1f else 0.6f
    }

    /**
     * Stable per-account swatch. The index is taken from the row id, not the
     * adapter position, so a row keeps its colour when the list reorders —
     * a colour that jumps between accounts as the list is rebuilt is worse
     * than no colour at all.
     */
    private fun accentFor(id: Long): Int = SWATCHES[(id % SWATCHES.size).toInt()]

    private fun formatWhen(millis: Long): String =
        DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private companion object {
        val SWATCHES = intArrayOf(Color.parseColor("#2E7D32"), Color.parseColor("#1565C0"), Color.parseColor("#6A1B9A"), Color.parseColor("#C62828"))
    }
}
