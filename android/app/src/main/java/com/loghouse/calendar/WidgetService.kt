package com.loghouse.calendar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        EventFactory(applicationContext)
}

private class EventFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetEvent> = emptyList()

    override fun onCreate() {}
    override fun onDataSetChanged() { items = EventStore.load(context) }
    override fun onDestroy() { items = emptyList() }
    override fun getCount() = items.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true

    override fun getViewAt(position: Int): RemoteViews {
        val ev = items[position]
        val row = RemoteViews(context.packageName, R.layout.widget_item)

        row.setTextViewText(R.id.itemTitle, ev.title)
        row.setTextViewText(
            R.id.itemTime,
            if (ev.allDay) "Весь день" else "${ev.start} — ${ev.end}"
        )
        row.setInt(R.id.itemBar, "setBackgroundColor", parse(ev.color))

        // a task carries a tick box; a plain event does not
        if (ev.isTask) {
            row.setViewVisibility(R.id.itemCheck, android.view.View.VISIBLE)
            row.setImageViewResource(
                R.id.itemCheck,
                if (ev.done) R.drawable.check_on else R.drawable.check_off
            )
            row.setOnClickFillInIntent(R.id.itemCheck, Intent().apply {
                putExtra(MainActivity.EXTRA_ROUTE, "toggle")
                putExtra(MainActivity.EXTRA_ID, ev.id)
            })
        } else {
            row.setViewVisibility(R.id.itemCheck, android.view.View.GONE)
        }

        row.setInt(R.id.itemTitle, "setTextColor",
            if (ev.done) Color.parseColor("#8E8BA8") else Color.parseColor("#ECEBF5"))

        row.setOnClickFillInIntent(R.id.itemRoot, Intent().apply {
            putExtra(MainActivity.EXTRA_ROUTE, "event")
            putExtra(MainActivity.EXTRA_ID, ev.id)
        })
        return row
    }

    private fun parse(hex: String): Int =
        try { Color.parseColor(hex) } catch (e: Exception) { Color.parseColor("#7C6DF2") }
}
