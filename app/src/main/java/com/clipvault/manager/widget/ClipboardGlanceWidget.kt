package com.clipvault.manager.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.clipvault.manager.app.MainActivity
import com.clipvault.manager.data.local.ClipDatabase
import com.clipvault.manager.data.local.entity.ClipEntity
import kotlinx.coroutines.flow.first

// Pre-resolved ColorProviders (glance.unit.ColorProvider takes an int ARGB).
// Glance flags the int constructor as RestrictedApi in its library config; the
// top-level ColorProvider(int) is the only public entry point for app code in
// 1.1.x, so we suppress the lint check here.
@SuppressLint("RestrictedApi")
private val WidgetBackground = ColorProvider(0xFF14131A.toInt())
@SuppressLint("RestrictedApi")
private val WidgetSurface = ColorProvider(0xFF24232C.toInt())
@SuppressLint("RestrictedApi")
private val WidgetTextPrimary = ColorProvider(0xFFE6E6EC.toInt())
@SuppressLint("RestrictedApi")
private val WidgetTextSecondary = ColorProvider(0xFFBDBDC8.toInt())

/**
 * Lock-screen / home-screen widget showing the 5 most recent clips.
 * Tap → opens the app.
 */
class ClipboardGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val clips = readRecentClips(context)
        provideContent {
            WidgetContent(clips)
        }
    }

    @Composable
    private fun WidgetContent(clips: List<ClipEntity>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .background(WidgetBackground)
                .cornerRadius(20.dp)
        ) {
            Text(
                text = "ClipVault",
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = GlanceModifier.size(6.dp))
            if (clips.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing copied yet",
                        style = TextStyle(
                            color = WidgetTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    clips.take(5).forEach { clip ->
                        WidgetRow(clip)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetRow(clip: ClipEntity) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(WidgetSurface)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(actionLaunchMain()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clip.content.take(40).let { if (clip.content.length > 40) "$it…" else it },
                style = TextStyle(
                    color = WidgetTextPrimary,
                    fontSize = 13.sp
                ),
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }

    private fun actionLaunchMain(): Action = androidx.glance.action.actionStartActivity(
        android.content.ComponentName(
            "com.clipvault.manager",
            MainActivity::class.java.name
        )
    )

    private suspend fun readRecentClips(context: Context): List<ClipEntity> {
        return try {
            val db = androidx.room.Room.databaseBuilder(
                context.applicationContext,
                ClipDatabase::class.java,
                "clipboard.db"
            ).allowMainThreadQueries().build()
            db.clipDao().observeAll().first().take(20)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

class ClipboardGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClipboardGlanceWidget()
}