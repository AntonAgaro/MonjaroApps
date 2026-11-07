package app.monjaro.drivemode

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** One screen: the list of drive modes. The chosen one is set after each engine start. */
class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var modeGroup: RadioGroup
    private lateinit var footer: TextView

    private val radioValues = mutableMapOf<Int, Int>() // view id -> mode value
    private var shownModes: List<Int> = emptyList()
    private var updatingRadios = false

    private val refresh = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DriveModeService.start(this)
        setContentView(buildLayout())
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildLayout(): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(32))
        }
        column.addView(text(getString(R.string.app_name), 30f, PRIMARY, bold = true))
        column.addView(text(getString(R.string.intro), 18f, SECONDARY).withTopMargin(8))

        modeGroup = RadioGroup(this).apply {
            setOnCheckedChangeListener { _, checkedId ->
                if (updatingRadios) return@setOnCheckedChangeListener
                val value = radioValues[checkedId] ?: return@setOnCheckedChangeListener
                Store.setTargetMode(this@MainActivity, value)
            }
        }
        column.addView(modeGroup.withTopMargin(16))

        footer = text("", 15f, SECONDARY).withTopMargin(24)
        column.addView(footer)

        return ScrollView(this).apply {
            setBackgroundColor(BACKGROUND)
            addView(column, MATCH_PARENT, WRAP_CONTENT)
        }
    }

    private fun render() {
        val status = DriveModeService.status
        renderModeChoices(status.supportedModes, Store.targetMode(this))
        footer.text = if (status.connected) {
            getString(R.string.current_mode, DriveModes.label(this, status.currentMode))
        } else {
            getString(R.string.no_connection, status.error ?: "…")
        }
    }

    /** Builds the list once, and again only if the car reports a different list of modes. */
    private fun renderModeChoices(supported: List<Int>, target: Int) {
        val base = supported.ifEmpty { DriveModes.DEFAULT_CHOICES }
        val modes = listOf(DriveModes.OFF) + base + listOf(target).filter { it != DriveModes.OFF && it !in base }
        updatingRadios = true
        if (modes != shownModes) {
            shownModes = modes
            modeGroup.removeAllViews()
            radioValues.clear()
            for (value in modes) {
                val button = RadioButton(this).apply {
                    id = View.generateViewId()
                    text = DriveModes.label(this@MainActivity, value)
                    textSize = 22f
                    setTextColor(PRIMARY)
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                }
                radioValues[button.id] = value
                modeGroup.addView(button)
            }
        }
        val checkedId = radioValues.entries.firstOrNull { it.value == target }?.key
        if (checkedId != null && modeGroup.checkedRadioButtonId != checkedId) modeGroup.check(checkedId)
        updatingRadios = false
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun <T : View> T.withTopMargin(marginDp: Int): T = apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(marginDp) }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        val BACKGROUND = Color.rgb(18, 20, 24)
        val PRIMARY = Color.rgb(236, 238, 242)
        val SECONDARY = Color.rgb(160, 166, 176)
    }
}
