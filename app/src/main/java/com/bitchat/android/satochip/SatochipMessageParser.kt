package com.bitchat.android.satochip

import android.util.Log
import com.bitchat.android.nostr.NostrCrypto
import java.security.MessageDigest

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
    
    /**
     * Verify a signed message using the provided public key
     * @param messageContent The original message content (without signature)
     * @param signatureHex The signature in hexadecimal format
     * @param publicKeyHex The public key in hexadecimal format
     * @return true if the signature is valid
     */
    fun verifySignedMessage(messageContent: String, signatureHex: String, publicKeyHex: String): Boolean {
        return try {
            // Hash the message content (same as when signing)
            val messageHash = hashMessageContent(messageContent)
            
            // Verify using BIP-340 Schnorr verification
            val isValid = NostrCrypto.schnorrVerify(messageHash, signatureHex, publicKeyHex)
            
            Log.d(TAG, "Signature verification result: $isValid for message: '$messageContent'")
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signature: ${e.message}")
            false
        }
    }
    
    /**
     * Hash message content for signing/verification (same as SatochipService)
     * @param messageContent The message content to hash
     * @return 32-byte hash
     */
    private fun hashMessageContent(messageContent: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(messageContent.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Get display content for a parsed message
     * @param parsedMessage The parsed message
     * @return Content to display to the user (with ~signed indicator if signed)
     */
    fun getDisplayContent(parsedMessage: ParsedMessage): String {
        return if (parsedMessage.isSigned) {
            "${parsedMessage.content}~signed"
        } else {
            parsedMessage.content
        }
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
