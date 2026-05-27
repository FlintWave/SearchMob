package org.searchmob.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.searchmob.R

/**
 * Home-screen widget: a single, static, tappable search bar with SearchMob branding. It renders no
 * query text, history, or results — only the affordance to open the in-app Search screen — so no
 * query data ever lives on the launcher surface.
 *
 * Colors come from [GlanceTheme] (Material3), which resolves day/night automatically, so the widget
 * stays legible in both light and dark system themes.
 */
class SearchWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxWidth()
                    .background(GlanceTheme.colors.background)
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App name above the bar, so the widget is recognisable as SearchMob in the picker/grid.
            Text(
                text = context.getString(R.string.app_name),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                modifier = GlanceModifier.padding(start = 8.dp, bottom = 6.dp),
            )

            // Tappable "search bar": launches MainActivity straight to the Search screen.
            Row(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(24.dp)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .clickable(actionStartActivity(SearchDeepLink.intent(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_search_hint),
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                        ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}
