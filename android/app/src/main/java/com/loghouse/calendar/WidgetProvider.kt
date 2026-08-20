package com.loghouse.calendar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

class WidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_ROW = "com.loghouse.calendar.TOGGLE_DONE"

        /** Redraws every placed widget — called after the web app pushes new events. */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { id -> draw(context, mgr, id) }
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widgetList)
        }

        private fun draw(context: Context, mgr: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget)
            val (dow, date) = EventStore.day(context)
            views.setTextViewText(R.id.widgetDow, dow)
            views.setTextViewText(R.id.widgetDate, date.ifBlank { "ЛогХаус" })

            val hasEvents = EventStore.load(context).isNotEmpty()
            views.setViewVisibility(R.id.widgetList, if (hasEvents) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widgetEmpty, if (hasEvents) View.GONE else View.VISIBLE)

            // the list is fed by WidgetService; the data uri keeps each widget's adapter distinct
            val svc = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("loghouse://widget/$id")
            }
            views.setRemoteAdapter(R.id.widgetList, svc)

            views.setOnClickPendingIntent(R.id.widgetAdd, openApp(context, "create", id * 10 + 1))
            views.setOnClickPendingIntent(R.id.widgetHeader, openApp(context, "day", id * 10 + 2))
            views.setOnClickPendingIntent(R.id.widgetEmpty, openApp(context, "create", id * 10 + 3))

            // one template for every row; each row fills in its own route and id
            val rowIntent = Intent(context, WidgetProvider::class.java).apply { action = ACTION_ROW }
            views.setPendingIntentTemplate(
                R.id.widgetList,
                PendingIntent.getBroadcast(
                    context, id * 10 + 4, rowIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            mgr.updateAppWidget(id, views)
        }

        private fun openApp(context: Context, route: String, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(MainActivity.EXTRA_ROUTE, route)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            return PendingIntent.getActivity(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> draw(context, mgr, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_ROW) {
            val id = intent.getStringExtra(MainActivity.EXTRA_ID) ?: return
            when (intent.getStringExtra(MainActivity.EXTRA_ROUTE)) {
                // ticking a task happens in the widget itself, no app needed
                "toggle" -> { EventStore.toggleDone(context, id); refresh(context) }
                else -> context.startActivity(
                    Intent(context, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(MainActivity.EXTRA_ROUTE, "event")
                        putExtra(MainActivity.EXTRA_ID, id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
            }
            return
        }
        super.onReceive(context, intent)
    }
}
