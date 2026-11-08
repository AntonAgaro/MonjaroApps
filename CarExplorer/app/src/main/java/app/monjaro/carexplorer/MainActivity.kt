package app.monjaro.carexplorer

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One screen: functions, sensors and the change log, with search and value details. */
class MainActivity : Activity() {

    private enum class Tab { FUNCTIONS, SENSORS, CHANGES }

    private sealed class Row {
        data class Function(val item: Catalog.Item) : Row()
        data class Sensor(val item: Catalog.Item) : Row()
        data class Change(val change: Explorer.Change) : Row()
        data class Manual(val id: Int) : Row()
        data class Message(val text: String) : Row()
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var tab = Tab.FUNCTIONS
    private var rows: List<Row> = emptyList()
    private var renderedVersion = -1

    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var onlySupported: CheckBox
    private lateinit var tabButtons: Map<Tab, Button>
    private lateinit var watchButton: Button
    private val adapter = RowAdapter()

    private val refresh = object : Runnable {
        override fun run() {
            if (Explorer.version != renderedVersion) render()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
        Explorer.start(this)
    }

    override fun onResume() {
        super.onResume()
        Explorer.start(this) // retries the connection if it failed before
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    // ---- layout ----

    private fun buildLayout(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            setPadding(dp(16), dp(12), dp(16), 0)
        }
        column.addView(text(getString(R.string.app_name), 24f, PRIMARY, bold = true))
        status = text("", 15f, SECONDARY)
        column.addView(status)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabButtons = Tab.entries.associateWith { t -> button("") { tab = t; render() }.also { buttons.addView(it) } }
        buttons.addView(button(getString(R.string.action_rescan)) { Explorer.rescan() })
        watchButton = button("") { Explorer.setWatching(!Explorer.watching) }
        buttons.addView(watchButton)
        buttons.addView(button(getString(R.string.action_save)) { saveReport() })
        column.addView(HorizontalScrollView(this).apply { addView(buttons) })

        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        search = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
            textSize = 17f
            setTextColor(PRIMARY)
            setHintTextColor(DIM)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = render()
            })
        }
        filters.addView(search, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        onlySupported = CheckBox(this).apply {
            text = getString(R.string.only_supported)
            textSize = 16f
            setTextColor(PRIMARY)
            isChecked = true
            setOnCheckedChangeListener { _, _ -> render() }
        }
        filters.addView(onlySupported)
        column.addView(filters)

        val list = ListView(this).apply {
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, position, _ -> onRowClick(rows[position]) }
        }
        column.addView(list, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        return column
    }

    // ---- rendering ----

    private fun render() {
        renderedVersion = Explorer.version
        val cat = Explorer.catalog
        status.text = statusText(cat)

        val changes = Explorer.changes()
        tabButtons.getValue(Tab.FUNCTIONS).text = getString(R.string.tab_functions)
        tabButtons.getValue(Tab.SENSORS).text = getString(R.string.tab_sensors)
        tabButtons.getValue(Tab.CHANGES).text = getString(R.string.tab_changes, changes.size)
        tabButtons.forEach { (t, b) -> b.setTextColor(if (t == tab) ACCENT else PRIMARY) }
        watchButton.text = getString(if (Explorer.watching) R.string.action_watch_on else R.string.action_watch_off)
        watchButton.setTextColor(if (Explorer.watching) ACCENT else PRIMARY)
        onlySupported.visibility = if (tab == Tab.CHANGES) View.GONE else View.VISIBLE

        rows = buildRows(cat, changes)
        adapter.notifyDataSetChanged()
    }

    private fun statusText(cat: Catalog?): String = when {
        cat == null -> getString(R.string.status_connecting)
        cat.functions.isEmpty() -> getString(R.string.status_no_catalog)
        !EcarxCar.isConnected -> getString(R.string.status_no_connection, EcarxCar.lastError ?: "…")
        Explorer.isScanning -> getString(R.string.status_scanning, Explorer.scanDone, Explorer.scanTotal)
        else -> getString(
            R.string.status_ready,
            Explorer.probes.values.count { it.isSupported }, cat.functions.size,
            Explorer.sensorProbes.values.count { it.isSupported }, cat.sensors.size,
        )
    }

    private fun buildRows(cat: Catalog?, changes: List<Explorer.Change>): List<Row> {
        val query = search.text.toString().trim()
        val words = query.uppercase(Locale.US).split(' ', '_').filter { it.isNotEmpty() }
        fun matches(item: Catalog.Item): Boolean {
            val haystack = "${item.name} ${item.group} ${item.id}".uppercase(Locale.US)
            return words.all { it in haystack }
        }

        val result = mutableListOf<Row>()
        when (tab) {
            Tab.FUNCTIONS -> {
                val typedId = parseNumber(query)?.toInt()
                if (typedId != null && cat?.item(typedId) == null) result += Row.Manual(typedId)
                cat?.functions.orEmpty()
                    .filter { matches(it) && (!onlySupported.isChecked || Explorer.probes[it.id]?.isSupported == true) }
                    .mapTo(result) { Row.Function(it) }
            }
            Tab.SENSORS -> cat?.sensors.orEmpty()
                .filter { matches(it) && (!onlySupported.isChecked || Explorer.sensorProbes[it.id]?.isSupported == true) }
                .mapTo(result) { Row.Sensor(it) }
            Tab.CHANGES -> changes
                .filter { c -> words.all { it in Explorer.name(c.id).uppercase(Locale.US) } }
                .mapTo(result) { Row.Change(it) }
        }
        if (result.isEmpty()) {
            result += Row.Message(getString(if (tab == Tab.CHANGES) R.string.empty_changes else R.string.empty_list))
        }
        return result
    }

    /** Title and detail line for one row. */
    private fun describe(row: Row): Pair<String, String> {
        val cat = Explorer.catalog
        return when (row) {
            is Row.Function -> {
                val p = Explorer.probes[row.item.id]
                row.item.name to listOfNotNull(
                    "${row.item.id}", Explorer.hex(row.item.id), row.item.group, p?.status ?: "—",
                    p?.let { probe -> valuesText(probe).ifEmpty { probe.floatValue?.toString().orEmpty() } }
                        ?.takeIf { it.isNotEmpty() },
                ).joinToString("  ·  ")
            }
            is Row.Sensor -> {
                val p = Explorer.sensorProbes[row.item.id]
                row.item.name to listOfNotNull(
                    "${row.item.id}", p?.status ?: "—",
                    p?.event?.let { "event ${cat?.valueLabel(it)}" },
                    p?.value?.let { "value $it" },
                ).joinToString("  ·  ")
            }
            is Row.Change -> {
                val c = row.change
                val zone = if (c.zone == null) "" else " [${cat?.zoneLabel(c.zone)}]"
                "${clock.format(Date(c.time))}  ${Explorer.name(c.id)}$zone" to
                    "${cat?.valueLabel(c.old)}  →  ${cat?.valueLabel(c.new)}"
            }
            is Row.Manual -> getString(R.string.unknown_id, row.id) to Explorer.hex(row.id)
            is Row.Message -> row.text to ""
        }
    }

    private fun valuesText(p: Explorer.Probe): String {
        val cat = Explorer.catalog ?: return ""
        return p.values.entries.joinToString(", ") { (zone, v) ->
            if (zone == null) cat.valueLabel(v) else "${cat.zoneLabel(zone)}=${cat.valueLabel(v)}"
        }
    }

    // ---- actions ----

    private fun onRowClick(row: Row) {
        when (row) {
            is Row.Function -> showFunction(row.item.id)
            is Row.Manual -> Explorer.probe(row.id) { runOnUiThread { showFunction(row.id) } }
            is Row.Change -> {
                val sensor = Explorer.catalog?.sensors?.firstOrNull { it.id == row.change.id }
                if (sensor != null) showSensor(sensor) else showFunction(row.change.id)
            }
            is Row.Sensor -> showSensor(row.item)
            is Row.Message -> Unit
        }
    }

    private fun showFunction(id: Int) {
        val cat = Explorer.catalog ?: return
        val p = Explorer.probes[id]
        val message = buildString {
            appendLine("ID: $id  (${Explorer.hex(id)})")
            cat.item(id)?.let { appendLine("Group: ${it.group}") }
            appendLine("Status: ${p?.status ?: "—"}")
            if (p != null) {
                appendLine("Zones: ${if (p.zones.isEmpty()) "global" else p.zones.joinToString { cat.zoneLabel(it) }}")
                appendLine("Value: ${valuesText(p).ifEmpty { "—" }}")
                p.floatValue?.let { appendLine("Float value: $it") }
                if (p.supported.isNotEmpty()) {
                    appendLine("Supported values:")
                    p.supported.forEach { appendLine("  • ${cat.valueLabel(it)}") }
                }
                p.error?.let { appendLine("Read error: $it") }
            }
        }
        AlertDialog.Builder(this)
            .setTitle(Explorer.name(id))
            .setMessage(message)
            .setPositiveButton(R.string.action_set) { _, _ -> chooseZone(id, p) }
            .setNeutralButton(R.string.action_read_again) { _, _ -> Explorer.probe(id) { runOnUiThread { showFunction(id) } } }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showSensor(item: Catalog.Item) {
        val cat = Explorer.catalog ?: return
        val p = Explorer.sensorProbes[item.id]
        val message = buildString {
            appendLine("ID: ${item.id}  (${Explorer.hex(item.id)})")
            appendLine("Status: ${p?.status ?: "—"}")
            appendLine("Event: ${cat.valueLabel(p?.event)}")
            appendLine("Value: ${p?.value ?: "—"}")
        }
        AlertDialog.Builder(this)
            .setTitle(item.name)
            .setMessage(message)
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun chooseZone(id: Int, p: Explorer.Probe?) {
        val zones = p?.zones.orEmpty()
        if (zones.isEmpty()) {
            chooseValue(id, null, p)
            return
        }
        val cat = Explorer.catalog ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_zone)
            .setItems(zones.map { cat.zoneLabel(it) }.toTypedArray()) { _, which -> chooseValue(id, zones[which], p) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun chooseValue(id: Int, zone: Int?, p: Explorer.Probe?) {
        val cat = Explorer.catalog ?: return
        val supported = p?.supported.orEmpty()
        val labels = supported.map { cat.valueLabel(it) } + getString(R.string.action_other_value)
        AlertDialog.Builder(this)
            .setTitle(Explorer.name(id))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which < supported.size) confirmWrite(id, zone, supported[which]) else enterValue(id, zone)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun enterValue(id: Int, zone: Int?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "1, 570491139, 0x22010103, 2.5"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.action_other_value)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = parseNumber(input.text.toString().trim())
                if (value == null) toast(getString(R.string.bad_number)) else confirmWrite(id, zone, value)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmWrite(id: Int, zone: Int?, value: Number) {
        val cat = Explorer.catalog ?: return
        val target = if (zone == null) Explorer.name(id) else "${Explorer.name(id)} [${cat.zoneLabel(zone)}]"
        AlertDialog.Builder(this)
            .setTitle(R.string.confirm_title)
            .setMessage(getString(R.string.confirm_write, target, cat.valueLabel(value)))
            .setPositiveButton(R.string.action_set) { _, _ ->
                Explorer.write(id, zone, value) { result -> runOnUiThread { toast(writeResultText(result)) } }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun writeResultText(result: String): String = when {
        result == "accepted" -> getString(R.string.write_accepted)
        result == "rejected" -> getString(R.string.write_rejected)
        result == "speed" -> getString(R.string.write_speed)
        else -> getString(R.string.write_error, result.removePrefix("error: "))
    }

    private fun saveReport() {
        Explorer.saveReport { result ->
            runOnUiThread {
                result.onSuccess { path ->
                    AlertDialog.Builder(this)
                        .setTitle(R.string.action_save)
                        .setMessage(getString(R.string.report_saved, path))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }.onFailure { toast(getString(R.string.report_failed, EcarxCar.describe(it))) }
            }
        }
    }

    // ---- helpers ----

    /** "123", "-5", "0x22010103" -> Int; "2.5" -> Float; anything else -> null. */
    private fun parseNumber(text: String): Number? = when {
        text.startsWith("0x", ignoreCase = true) -> text.substring(2).toLongOrNull(16)?.toInt()
        text.contains('.') -> text.toFloatOrNull()
        else -> text.toIntOrNull()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        isAllCaps = false
        setTextColor(PRIMARY)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private inner class RowAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView as? LinearLayout ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(4), dp(10), dp(4), dp(10))
                addView(text("", 18f, PRIMARY, bold = true))
                addView(text("", 14f, SECONDARY))
            }
            val row = rows[position]
            val (title, detail) = describe(row)
            val supported = when (row) {
                is Row.Function -> Explorer.probes[row.item.id]?.isSupported == true
                is Row.Sensor -> Explorer.sensorProbes[row.item.id]?.isSupported == true
                else -> true
            }
            (view.getChildAt(0) as TextView).apply {
                text = title
                setTextColor(if (supported) PRIMARY else DIM)
            }
            (view.getChildAt(1) as TextView).apply {
                text = detail
                visibility = if (detail.isEmpty()) View.GONE else View.VISIBLE
            }
            return view
        }
    }

    private companion object {
        val BACKGROUND = Color.rgb(18, 20, 24)
        val PRIMARY = Color.rgb(236, 238, 242)
        val SECONDARY = Color.rgb(160, 166, 176)
        val DIM = Color.rgb(100, 104, 112)
        val ACCENT = Color.rgb(255, 176, 32)
    }
}
