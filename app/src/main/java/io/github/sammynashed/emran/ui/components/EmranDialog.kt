package io.github.sammynashed.emran.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * Why these exist -- the "text jumps from left to right" glitch in Arabic.
 *
 * A dialog is its own window with its own Compose root, and that root starts from the
 * *phone's* layout direction (left-to-right on an English phone), not from the
 * direction the app chose. The first frame is therefore laid out left-to-right and only
 * a moment later corrected, so every dialog opened in Arabic visibly slid its text
 * from the left to the right. The correct direction is known before the dialog opens
 * (the app provides it at its root), so it is captured here, outside the window, and
 * re-applied as the very first thing inside it.
 */

/** Drop-in for [Dialog] that opens with the app's layout direction from the first frame. */
@Composable
fun EmranDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val direction = LocalLayoutDirection.current
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
    }
}

/**
 * Drop-in for Material 3's `AlertDialog`, with the same slots and the same look, built on
 * [EmranDialog] so its layout, including which side the buttons sit on, is right-to-left
 * from the first frame rather than after it.
 */
@Composable
fun EmranAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    properties: DialogProperties = DialogProperties()
) {
    EmranDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = properties.dismissOnBackPress,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            // Material's own dialogs size themselves (280-560dp); the platform default width
            // would squeeze long content.
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 720.dp),
            shape = shape,
            color = containerColor,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (title != null) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        Box(Modifier.padding(bottom = 16.dp)) { title() }
                    }
                }
                if (text != null) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Box(Modifier.weight(1f, fill = false).padding(bottom = 24.dp)) { text() }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}
