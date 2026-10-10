package io.github.sammynashed.emran

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetState
import io.github.sammynashed.emran.glance.state.EmranWidgetStateDefinition

class TestUpdateAppWidgetState {
    suspend fun test(context: Context, glanceId: GlanceId) {
        updateAppWidgetState<EmranWidgetState>(context, EmranWidgetStateDefinition, glanceId) {
            it
        }
    }
}
