package app.locator.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.locator.LocatorApp
import app.locator.QuickSaveActivity
import app.locator.R
import app.locator.data.ItemWithCurrent
import app.locator.data.Place
import app.locator.data.PlaceTree
import app.locator.ui.ageText
import kotlinx.coroutines.flow.first

private class WidgetModel(val title: String, val action: String, val last: String?)

/** "Last: <where>, <age>" for the quick item, or null when it has no location yet. */
internal fun lastSavedText(context: Context, current: ItemWithCurrent?, places: List<Place>): String? {
    val placePath = PlaceTree.pathOf(places, current?.currentPlaceId)
    val where = PlaceTree.whereLabel(current?.currentDescription, placePath) ?: current?.takeIf { it.hasGps }?.let {
        context.getString(R.string.location_gps, it.currentAccuracy?.toInt() ?: 0)
    }
    val at = current?.currentAt ?: return null
    return where?.let { context.getString(R.string.widget_last, it, ageText(context, at)) }
}

/** One tap opens the short foreground save screen, which takes the GPS fix for the quick-save item. */
class LocatorWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as LocatorApp).container
        val item = container.quickItem.current()
        val model = if (item == null) {
            WidgetModel(context.getString(R.string.app_name), context.getString(R.string.widget_no_item), null)
        } else {
            val current = container.repository.item(item.id).first()
            val last = lastSavedText(context, current, container.repository.places().first())
            WidgetModel(item.name, context.getString(R.string.widget_tap_to_save), last)
        }
        val open = Intent(context, QuickSaveActivity::class.java)
        provideContent { Content(model, open) }
    }
}

private val Background = ColorProvider(day = Color(0xFFE3EEEC), night = Color(0xFF182B2A))
private val Primary = ColorProvider(day = Color(0xFF00695F), night = Color(0xFF4FD1C5))
private val OnSurface = ColorProvider(day = Color(0xFF10201F), night = Color(0xFFF1F7F6))
private val Muted = ColorProvider(day = Color(0xFF3F5654), night = Color(0xFF93ADAA))
private val Tile = ColorProvider(day = Color(0xFF4FD1C5), night = Color(0xFF4FD1C5))

@Composable
private fun Content(model: WidgetModel, open: Intent) {
    Row(
        modifier = GlanceModifier.fillMaxSize().background(Background).cornerRadius(24.dp).padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(44.dp).background(Tile).cornerRadius(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_pin), contentDescription = null, modifier = GlanceModifier.size(24.dp))
        }
        Spacer(GlanceModifier.width(12.dp))
        Column {
            Text(model.title, style = TextStyle(color = OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Text(model.action, style = TextStyle(color = Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            model.last?.let { Text(it, style = TextStyle(color = Muted, fontSize = 11.sp), maxLines = 1) }
        }
    }
}

class LocatorWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LocatorWidget()
}
