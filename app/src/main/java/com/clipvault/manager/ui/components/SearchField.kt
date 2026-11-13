package com.clipvault.manager.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Tonal colours for the pill search fields on list screens (Snippets, Tags,
 * Collections, Search).
 *
 * The mock styles its search affordance as a filled `surfaceContainerHigh`
 * pill with no outline — that's what the Home search pill ships. The list
 * screens were left on the default outlined field with a visible 1dp border,
 * which read as a different component. This keeps every search field on the
 * same token as Home.
 */
@Composable
fun tonalSearchFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent
)
