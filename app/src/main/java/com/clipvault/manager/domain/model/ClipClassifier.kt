package com.clipvault.manager.domain.model

import com.clipvault.manager.data.local.entity.ClipType

object ClipClassifier {
    private val urlRegex = Regex("^(https?://|www\\.)[^\\s]+$", RegexOption.IGNORE_CASE)
    private val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val phoneRegex = Regex("^[+]?[0-9 ()\\-]{7,20}$")
    private val codeHintRegex = Regex("[{};=(){}\\[\\]]")
    private val colorHexRegex = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
    private val uuidRegex = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )
    private val ibanRegex = Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$")
    private val ipRegex = Regex(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$"
    )
    // Crypto addresses (BTC, ETH)
    private val btcRegex = Regex("^(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,62}$")
    private val ethRegex = Regex("^0x[0-9a-fA-F]{40}$")

    // One-time codes: a 4-8 digit code near a context keyword such as
    // "verification code is 482913", "OTP: 1234", "sign-in code 937201".
    private val otpContextRegex = Regex(
        "(?i)(?:code|otp|verification|one[\\s-]?time|login|sign[\\s-]?in|password|auth|pin|confirm)" +
            "[^0-9]{0,14}(\\d{4,8})"
    )

    fun classify(text: String): ClipType {
        val trimmed = text.trim()
        return when {
            extractOtp(trimmed) != null -> ClipType.OTP
            urlRegex.matches(trimmed) -> ClipType.URL
            colorHexRegex.matches(trimmed) -> ClipType.COLOR_HEX
            emailRegex.matches(trimmed) -> ClipType.EMAIL
            ibanRegex.matches(trimmed) -> ClipType.IBAN
            uuidRegex.matches(trimmed) -> ClipType.UUID
            ipRegex.matches(trimmed) -> ClipType.IP
            ethRegex.matches(trimmed) || btcRegex.matches(trimmed) -> ClipType.WALLET_ADDRESS
            phoneRegex.matches(trimmed) -> ClipType.PHONE
            trimmed.toLongOrNull() != null || trimmed.toDoubleOrNull() != null -> ClipType.NUMBER
            // JSON heuristic: starts with { or [ and ends with } or ] and has a colon
            (trimmed.startsWith("{") && trimmed.endsWith("}") && trimmed.contains(':')) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) -> ClipType.JSON
            codeHintRegex.findAll(trimmed).count() >= 3 -> ClipType.CODE
            else -> ClipType.TEXT
        }
    }

    /**
     * Extracts a one-time code (4-8 digits) when the text carries a context
     * keyword. Conservative on purpose: bare numbers are left as NUMBER so
     * phone numbers or other plain digits are never mis-handled.
     */
    fun extractOtp(text: String): String? =
        otpContextRegex.find(text.trim())?.groupValues?.get(1)

    /**
     * Parse a #RRGGBB / #AARRGGBB string to an ARGB Long.
     * Returns null if not a valid hex color.
     */
    fun parseHexColor(hex: String): Long? {
        if (!colorHexRegex.matches(hex.trim())) return null
        val raw = hex.trim().removePrefix("#")
        return when (raw.length) {
            3 -> { // #RGB → #RRGGBB
                val expanded = raw.map { "$it$it" }.joinToString("")
                parseHexColor("#$expanded")
            }
            6 -> ("FF$raw").toLong(16)
            8 -> raw.toLong(16)
            else -> null
        }
    }
}