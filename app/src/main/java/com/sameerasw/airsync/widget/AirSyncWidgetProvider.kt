package com.sameerasw.airsync.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R

class AirSyncWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "AirSyncWidget"

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, AirSyncWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            val provider = AirSyncWidgetProvider()
            provider.onUpdate(context, appWidgetManager, widgetIds)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "Updating ${appWidgetIds.size} widgets")

        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_airsync)

        try {
            // Set the WIP content
            views.setTextViewText(R.id.widget_app_title, "AirSync")
            views.setTextViewText(R.id.widget_wip_message, "⚠️ Work In Progress")
            views.setTextViewText(R.id.widget_subtitle, "Widget under development")

            // Set click listener to open app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
            Log.d(TAG, "Widget $widgetId updated with WIP message")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId: ${e.message}")
        }
    }
}
