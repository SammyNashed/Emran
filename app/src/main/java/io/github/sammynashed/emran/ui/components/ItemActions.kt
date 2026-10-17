package io.github.sammynashed.emran.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.unit.dp

/**
 * Bookmark, copy and share: the same three buttons, in the same order and style, on every
 * hadith and every dhikr, so a reader never has to hunt for them on one screen that they
 * found on another.
 */
@Composable
fun ItemActions(
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sky = io.github.sammynashed.emran.ui.design.LocalSky.current
    val idle = sky.inkSoft
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggleBookmark, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
                tint = if (bookmarked) sky.ink else idle
            )
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = idle, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onShare, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = idle, modifier = Modifier.size(20.dp))
        }
    }
}
