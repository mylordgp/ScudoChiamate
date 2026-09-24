package it.scudochiamate

data class CallLogEntry(
    val number: String,
    val name: String,
    val type: Int,
    val date: String,
    val blocked: Boolean,
    val whitelisted: Boolean = false
)
