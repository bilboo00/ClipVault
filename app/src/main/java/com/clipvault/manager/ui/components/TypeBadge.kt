package com.clipvault.manager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clipvault.manager.data.local.entity.ClipType

@Composable
fun TypeBadge(type: ClipType, modifier: Modifier = Modifier) {
    val icon = typeIcon(type)
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
            Text(
                type.label(),
                style = MaterialTheme.typography.labelMedium,
                color = tint
            )
        }
    }
}

@Composable
fun typeIcon(type: ClipType) = when (type) {
    ClipType.URL -> Icons.Filled.Link
    ClipType.EMAIL -> Icons.Filled.Email
    ClipType.PHONE -> Icons.Filled.Phone
    ClipType.CODE -> Icons.Filled.Code
    ClipType.NUMBER -> Icons.Filled.Numbers
    ClipType.TEXT -> Icons.Filled.TextFields
    ClipType.COLOR_HEX -> Icons.Filled.Palette
    ClipType.UUID -> Icons.Filled.Fingerprint
    ClipType.IBAN -> Icons.Filled.Token
    ClipType.JSON -> Icons.Outlined.DataObject
    ClipType.WALLET_ADDRESS -> Icons.Filled.Wallet
    ClipType.IP -> Icons.Outlined.Public
    ClipType.IMAGE -> Icons.Filled.Image
    ClipType.OTP -> Icons.Filled.Key
}

fun ClipType.label() = when (this) {
    ClipType.URL -> "URL"
    ClipType.EMAIL -> "Email"
    ClipType.PHONE -> "Phone"
    ClipType.CODE -> "Code"
    ClipType.NUMBER -> "Number"
    ClipType.TEXT -> "Text"
    ClipType.COLOR_HEX -> "Color"
    ClipType.UUID -> "UUID"
    ClipType.IBAN -> "IBAN"
    ClipType.JSON -> "JSON"
    ClipType.WALLET_ADDRESS -> "Wallet"
    ClipType.IP -> "IP"
    ClipType.IMAGE -> "Image"
    ClipType.OTP -> "OTP"
}