package it.scudochiamate.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entità Room: una chiamata bloccata.
 */
@Entity(tableName = "blocked_calls")
data class BlockedCall(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val phoneNumber: String,
    val reason: String,      // "FOREIGN_PREFIX" | "KNOWN_SPAM"
    val timestamp: Date
)
