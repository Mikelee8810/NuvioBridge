package com.tunombre.tvbridge

object AccessibilityEventTextFormatter {
    fun format(text: List<CharSequence?>): String =
        text.joinToString(limit = 3, truncated = "…") { it?.toString().orEmpty() }
}
