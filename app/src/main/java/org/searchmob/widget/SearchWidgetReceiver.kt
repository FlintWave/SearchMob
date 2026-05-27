package org.searchmob.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The broadcast receiver the launcher talks to for the SearchMob home-screen widget. It simply hands
 * the launcher our [SearchWidget]; all rendering happens in Glance.
 */
class SearchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SearchWidget()
}
