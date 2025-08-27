package com.bitchat.android.satochip

import android.util.Log

/**
 * Utility class for parsing Satochip signed messages
 */
object SatochipMessageParser {
    private const val TAG = "SatochipMessageParser"
    private const val SIGNATURE_SEPARATOR = "~"
    
    /**
     * Parse a message to extract content and signature if present
     * @param messageContent The full message content
     * @return ParsedMessage containing the message content and signature (if present)
     */
    fun parseMessage(messageContent: String): ParsedMessage {
        val lastSeparatorIndex = messageContent.lastIndexOf(SIGNATURE_SEPARATOR)
        
        return if (lastSeparatorIndex != -1 && lastSeparatorIndex < messageContent.length - 1) {
            // Message contains signature
            val content = messageContent.substring(0, lastSeparatorIndex)
            val signature = messageContent.substring(lastSeparatorIndex + 1)
            
            Log.d(TAG, "Parsed signed message - Content: '$content', Signature: '$signature'")
            
            ParsedMessage(
                content = content,
                signature = signature,
                isSigned = true
            )
        } else {
            // Message has no signature
            ParsedMessage(
                content = messageContent,
                signature = null,
                isSigned = false
            )
        }
    }
    
    /**
     * Check if a message contains a signature
     * @param messageContent The message content to check
     * @return true if the message contains a signature
     */
    fun isSignedMessage(messageContent: String): Boolean {
        val lastSeparatorIndex = messageContent.lastIndexOf(SIGNATURE_SEPARATOR)
        return lastSeparatorIndex != -1 && lastSeparatorIndex < messageContent.length - 1
    }
}

/**
 * Data class representing a parsed message
 */
data class ParsedMessage(
    val content: String,
    val signature: String?,
    val isSigned: Boolean
)
