package app.fieldwatch.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import app.fieldwatch.wear.WearMainActivity
import app.fieldwatch.wear.data.WearStateRepository
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class FieldwatchTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val summary = WearStateRepository.summary.value
        val trackerCount = summary.trackerCount
        val bleCount = summary.bleCount
        val wifiCount = summary.wifiCount

        val clickIntent = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(applicationContext.packageName)
                    .setClassName(WearMainActivity::class.java.name)
                    .build()
            )
            .build()

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(clickIntent)
            .setId("open_app")
            .build()

        val rootModifier = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .build()

        val titleText = LayoutElementBuilders.Text.Builder()
            .setText("FIELDWATCH")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(12f))
                    .setColor(ColorBuilders.argb(0xFFB0B0B0.toInt()))
                    .build()
            )
            .build()

        val statsText = LayoutElementBuilders.Text.Builder()
            .setText("$wifiCount WF  •  $bleCount BLE")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(16f))
                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                    .setColor(ColorBuilders.argb(0xFF00E5FF.toInt()))
                    .build()
            )
            .build()

        val alertText = LayoutElementBuilders.Text.Builder()
            .setText(if (trackerCount > 0) "⚠️ $trackerCount TRACKER ALERT" else "✔ NO TRACKERS")
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(13f))
                    .setColor(
                        ColorBuilders.argb(
                            if (trackerCount > 0) 0xFFFF1744.toInt() else 0xFF00E676.toInt()
                        )
                    )
                    .build()
            )
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setModifiers(rootModifier)
            .addContent(titleText)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(6f)).build())
            .addContent(statsText)
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(4f)).build())
            .addContent(alertText)
            .build()

        val layout = LayoutElementBuilders.Layout.Builder().setRoot(column).build()
        val timelineEntry = TimelineBuilders.TimelineEntry.Builder().setLayout(layout).build()
        val timeline = TimelineBuilders.Timeline.Builder().addTimelineEntry(timelineEntry).build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(15_000L)
            .build()

        return ImmediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()
        return ImmediateFuture(resources)
    }

    private class ImmediateFuture<T>(private val value: T) : ListenableFuture<T> {
        override fun cancel(mayInterruptIfRunning: Boolean) = false
        override fun isCancelled() = false
        override fun isDone() = true
        override fun get(): T = value
        override fun get(timeout: Long, unit: TimeUnit): T = value
        override fun addListener(listener: Runnable, executor: Executor) {
            executor.execute(listener)
        }
    }
}
