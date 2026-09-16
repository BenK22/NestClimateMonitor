package ca.humiditylogger

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class WidgetConfigurationActivity : Activity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val transparent = Switch(this).apply {
            text = "Transparent background"
            setTextColor(Color.rgb(238, 246, 243))
            textSize = 17f
        }
        val border = Switch(this).apply {
            text = "Show border"
            setTextColor(Color.rgb(238, 246, 243))
            textSize = 17f
            isChecked = true
        }
        val add = Button(this).apply {
            text = "ADD WIDGET"
            setTextColor(Color.rgb(9, 16, 14))
            setBackgroundColor(Color.rgb(112, 219, 181))
            setOnClickListener {
                WidgetAppearance.save(this@WidgetConfigurationActivity, widgetId, transparent.isChecked, border.isChecked)
                val manager = AppWidgetManager.getInstance(this@WidgetConfigurationActivity)
                val provider = manager.getAppWidgetInfo(widgetId)?.provider?.className
                if (provider == GraphWidgetProvider::class.java.name) {
                    GraphWidgetProvider.updateWidget(this@WidgetConfigurationActivity, manager, widgetId)
                } else {
                    ClimateWidgetProvider.updateWidget(this@WidgetConfigurationActivity, manager, widgetId)
                }
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(28), dp(28), dp(28), dp(28))
            setBackgroundColor(Color.rgb(9, 16, 14))
            addView(TextView(this@WidgetConfigurationActivity).apply {
                text = "Widget appearance"
                textSize = 26f
                setTextColor(Color.rgb(238, 246, 243))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(transparent, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(24) })
            addView(border, LinearLayout.LayoutParams(-1, dp(64)))
            addView(add, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(28) })
        }
        setContentView(root)
    }
}
