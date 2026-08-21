package com.clipvault.manager.widget

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.clipvault.manager.util.ClipUtils
import kotlinx.coroutines.flow.first

// ── Colours ──────────────────────────────────────────────────────────────────

@SuppressLint("RestrictedApi")
private val WidgetBackground = ColorProvider(0xFF14131A.toInt())
@SuppressLint("RestrictedApi")
private val WidgetSurface = ColorProvider(0xFF24232C.toInt())
@SuppressLint("RestrictedApi")
private val WidgetTextPrimary = ColorProvider(0xFFE6E6EC.toInt())
@SuppressLint("RestrictedApi")
private val WidgetTextSecondary = ColorProvider(0xFFBDBDC8.toInt())
@SuppressLint("RestrictedApi")
private val WidgetAccent = ColorProvider(0xFF7C5CFC.toInt())
@SuppressLint("RestrictedApi")
private val WidgetPinBadge = ColorProvider(0xFFFFD54F.toInt())

// ── DataStore (same file name as SettingsManager → same process instance) ────

private val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_MASK = booleanPreferencesKey("mask_sensitive")
private val KEY_BIOMETRIC = booleanPreferencesKey("require_biometric")

// ── Widget ───────────────────────────────────────────────────────────────────

/**
 * Home / lock-screen widget.
 *
 * - Pinned clips shown first in a dedicated section
 * - Tap row → opens app
 * - Tap copy icon → copies clip text to clipboard (no app open)
 * - Respects "Mask sensitive content" setting (shows •••••)
 */
class ClipboardGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val clips = readClips(context)
        val masking = readMasking(context)
        provideContent { WidgetContent(clips, masking) }
    }

    @Composable
    private fun WidgetContent(clips: List<ClipEntity>, masking: Boolean) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
                .background(WidgetBackground)
                .cornerRadius(20.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ClipVault",
                    style = TextStyle(
                        color = WidgetTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (masking) {
                    Text(
                        text = "hidden",
                        style = TextStyle(color = WidgetTextSecondary, fontSize = 10.sp),
                        modifier = GlanceModifier.padding(
                            horizontal = 8.dp,
                            vertical = 2.dp
                        ).background(WidgetSurface).cornerRadius(8.dp).padding(
                            horizontal = 8.dp,
                            vertical = 2.dp
                        )
                    )
                }
            }
            Spacer(modifier = GlanceModifier.size(6.dp))
            if (clips.isEmpty()) {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing copied yet",
                        style = TextStyle(color = WidgetTextSecondary, fontSize = 12.sp)
                    )
                }
            } else {
                val pinned = clips.filter { it.isPinned }
                val recent = clips.filter { !it.isPinned }
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    if (pinned.isNotEmpty()) {
                        SectionLabel("Pinned")
                        pinned.take(3).forEach { WidgetRow(it, masking) }
                        if (recent.isNotEmpty()) Spacer(modifier = GlanceModifier.size(4.dp))
                    }
                    if (recent.isNotEmpty()) {
                        if (pinned.isEmpty()) SectionLabel("Recent")
                        recent.take(if (pinned.isNotEmpty()) 3 else 5).forEach { WidgetRow(it, masking) }
                    }
                }
            }
        }
    }

    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            style = TextStyle(color = WidgetTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
            modifier = GlanceModifier.padding(bottom = 2.dp)
        )
    }

    @Composable
    private fun WidgetRow(clip: ClipEntity, masking: Boolean) {
        val display = if (clip.isLocked) {
            "🔒 Locked"
        } else if (masking) "••••••••" else clip.content.take(40).let {
            if (clip.content.length > 40) "$it…" else it
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(WidgetSurface)
                .cornerRadius(14.dp)
                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .clickable(actionLaunchMain()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                if (clip.isPinned) {
                    Text(
                        text = "★",
                        style = TextStyle(color = WidgetPinBadge, fontSize = 10.sp)
                    )
                }
                Text(
                    text = display,
                    style = TextStyle(color = WidgetTextPrimary, fontSize = 13.sp)
                )
            }
            Box(
                modifier = GlanceModifier
                    .padding(start = 6.dp)
                    .clickable(actionCopyClip(clip.id)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⟳",
                    style = TextStyle(color = WidgetAccent, fontSize = 16.sp)
                )
            }
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private fun actionLaunchMain(): Action = androidx.glance.action.actionStartActivity(
        android.content.ComponentName("com.clipvault.manager", MainActivity::class.java.name)
    )

    private fun actionCopyClip(clipId: Long): Action {
        val params = actionParametersOf(CLIP_ID_KEY to clipId)
        return actionRunCallback<CopyClipAction>(params)
    }

    // ── Data helpers ─────────────────────────────────────────────────────────

    private suspend fun readClips(context: Context): List<ClipEntity> {
        return try {
            widgetDatabase(context).clipDao().observeAll().first().take(8)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun readMasking(context: Context): Boolean =
        try {
            val prefs = context.dataStore.data.first()
            prefs[KEY_MASK] == true || prefs[KEY_BIOMETRIC] == true
        } catch (_: Exception) {
            false
        }

    companion object {
        val CLIP_ID_KEY = ActionParameters.Key<Long>("clip_id")
    }
}

// ── Copy-to-clipboard callback ───────────────────────────────────────────────

class CopyClipAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val clipId = parameters[ClipboardGlanceWidget.CLIP_ID_KEY] ?: return
        try {
            val db = widgetDatabase(context)
            val clip = db.clipDao().getById(clipId) ?: return
            if (clip.isLocked) return
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipUtils.clipDataFor(context, clip.content, clip.imageUri))
            // Widget copies count toward use-limit expiry like in-app copies.
            db.clipDao().incrementUseCount(clipId)
        } catch (_: Exception) {
        }
    }
}

// ── Process-level database holder for the widget ─────────────────────────────

/**
 * Widget host process has its own Hilt graph (none in our case — the widget
 * doesn't depend on the main app module), so the widget builds its own Room
 * instance. Reusing a process-level singleton avoids reopening SQLite every
 * time a widget refresh fires (Glance rebuilds content on every update).
 *
 * .allowMainThreadQueries() is required because provideGlance renders on the
 * main thread, and .fallbackToDestructiveMigration() is acceptable because the
 * widget rebuilds its data from the main app on next open — the main
 * DatabaseModule owns the real schema.
 */
private object WidgetDatabaseHolder {
    @Volatile
    private var instance: ClipDatabase? = null

    fun get(context: Context): ClipDatabase {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: androidx.room.Room.databaseBuilder(
                context.applicationContext,
                ClipDatabase::class.java,
                "clipboard.db"
            )
                .addMigrations(*ClipDatabase.MIGRATIONS)
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                .also { instance = it }
        }
    }
}

private fun widgetDatabase(context: Context): ClipDatabase = WidgetDatabaseHolder.get(context)

// ── Receiver ─────────────────────────────────────────────────────────────────

class ClipboardGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClipboardGlanceWidget()
}
