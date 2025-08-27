package com.bitchat.android.satochip

import android.util.Log
import org.satochip.io.APDUResponse
import org.satochip.client.ApplicationStatus
import java.nio.charset.StandardCharsets

/**
 * Utility functions for Satochip operations
 */
object SatochipUtils {
    
    private const val TAG = "SatochipUtils"
    
    // MARK: - Status Word Constants
    
    object StatusWords {
        const val SW_SUCCESS = 0x9000
        const val SW_WRONG_LENGTH = 0x6700
        const val SW_SECURITY_STATUS_NOT_SATISFIED = 0x6982
        const val SW_FILE_INVALID = 0x6983
        const val SW_DATA_INVALID = 0x6984
        const val SW_CONDITIONS_NOT_SATISFIED = 0x6985
        const val SW_COMMAND_NOT_ALLOWED = 0x6986
        const val SW_WRONG_P1P2 = 0x6A86
        const val SW_WRONG_DATA = 0x6A80
        const val SW_FILE_NOT_FOUND = 0x6A82
        const val SW_RECORD_NOT_FOUND = 0x6A83
        const val SW_WRONG_P1P2_LENGTH = 0x6A87
        const val SW_REFERENCE_DATA_NOT_FOUND = 0x6A88
        const val SW_INS_NOT_SUPPORTED = 0x6D00
        const val SW_CLA_NOT_SUPPORTED = 0x6E00
        const val SW_UNKNOWN = 0x6F00
        const val SW_FILE_FULL = 0x6A84
        const val SW_LOGICAL_CHANNEL_NOT_SUPPORTED = 0x6881
        const val SW_SECURE_MESSAGING_NOT_SUPPORTED = 0x6882
        const val SW_WARNING_STATE_UNCHANGED = 0x6200
        const val SW_WARNING_STATE_CHANGED = 0x6300
        const val SW_WARNING_NO_INFORMATION = 0x6400
        const val SW_ERROR_NO_INFORMATION = 0x6F00
        const val SW_ERROR_INVALID_OBJECT = 0x6A80
        const val SW_ERROR_INVALID_OBJECT_REFERENCE = 0x6A81
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_TYPE = 0x6A82
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_VALUE = 0x6A83
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_LENGTH = 0x6A84
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET = 0x6A85
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH = 0x6A86
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1 = 0x6A87
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2 = 0x6A88
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3 = 0x6A89
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4 = 0x6A8A
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4_P5 = 0x6A8B
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4_P5_P6 = 0x6A8C
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4_P5_P6_P7 = 0x6A8D
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4_P5_P6_P7_P8 = 0x6A8E
        const val SW_ERROR_INVALID_OBJECT_REFERENCE_OFFSET_LENGTH_P1P2_P3_P4_P5_P6_P7_P8_P9 = 0x6A8F
    }
    
    // MARK: - Status Word Parsing
    
    /**
     * Get human-readable description for a status word
     */
    fun getStatusWordDescription(statusWord: Int): String {
        return when (statusWord) {
            StatusWords.SW_SUCCESS -> "Success"
            StatusWords.SW_WRONG_LENGTH -> "Wrong length"
            StatusWords.SW_SECURITY_STATUS_NOT_SATISFIED -> "Security status not satisfied"
            StatusWords.SW_FILE_INVALID -> "File invalid"
            StatusWords.SW_DATA_INVALID -> "Data invalid"
            StatusWords.SW_CONDITIONS_NOT_SATISFIED -> "Conditions not satisfied"
            StatusWords.SW_COMMAND_NOT_ALLOWED -> "Command not allowed"
            StatusWords.SW_WRONG_P1P2 -> "Wrong P1P2"
            StatusWords.SW_WRONG_DATA -> "Wrong data"
            StatusWords.SW_FILE_NOT_FOUND -> "File not found"
            StatusWords.SW_RECORD_NOT_FOUND -> "Record not found"
            StatusWords.SW_WRONG_P1P2_LENGTH -> "Wrong P1P2 length"
            StatusWords.SW_REFERENCE_DATA_NOT_FOUND -> "Reference data not found"
            StatusWords.SW_INS_NOT_SUPPORTED -> "Instruction not supported"
            StatusWords.SW_CLA_NOT_SUPPORTED -> "Class not supported"
            StatusWords.SW_UNKNOWN -> "Unknown error"
            StatusWords.SW_FILE_FULL -> "File full"
            StatusWords.SW_LOGICAL_CHANNEL_NOT_SUPPORTED -> "Logical channel not supported"
            StatusWords.SW_SECURE_MESSAGING_NOT_SUPPORTED -> "Secure messaging not supported"
            StatusWords.SW_WARNING_STATE_UNCHANGED -> "Warning: state unchanged"
            StatusWords.SW_WARNING_STATE_CHANGED -> "Warning: state changed"
            StatusWords.SW_WARNING_NO_INFORMATION -> "Warning: no information"
            StatusWords.SW_ERROR_NO_INFORMATION -> "Error: no information"
            else -> "Unknown status word: 0x${statusWord.toString(16).uppercase()}"
        }
    }
    
    /**
     * Check if a status word indicates success
     */
    fun isSuccess(statusWord: Int): Boolean {
        return statusWord == StatusWords.SW_SUCCESS
    }
    
    /**
     * Check if a status word indicates a warning
     */
    fun isWarning(statusWord: Int): Boolean {
        return (statusWord and 0xFF00) == 0x6200 || (statusWord and 0xFF00) == 0x6300
    }
    
    /**
     * Check if a status word indicates an error
     */
    fun isError(statusWord: Int): Boolean {
        return (statusWord and 0xFF00) == 0x6900 || (statusWord and 0xFF00) == 0x6A00 || 
               (statusWord and 0xFF00) == 0x6B00 || (statusWord and 0xFF00) == 0x6C00 ||
               (statusWord and 0xFF00) == 0x6D00 || (statusWord and 0xFF00) == 0x6E00 ||
               (statusWord and 0xFF00) == 0x6F00
    }
    
    // MARK: - Response Validation
    
    /**
     * Validate an APDU response and return error message if failed
     */
    fun validateResponse(response: APDUResponse): String? {
        val statusWord = response.getSw()
        
        if (isSuccess(statusWord)) {
            return null
        }
        
        return getStatusWordDescription(statusWord)
    }
    
    /**
     * Log response details for debugging
     */
    fun logResponse(response: APDUResponse, operation: String) {
        val statusWord = response.getSw()
        val data = response.getData()
        
        Log.d(TAG, "$operation - Status: 0x${statusWord.toString(16).uppercase()} " +
                   "(${getStatusWordDescription(statusWord)})")
        
        if (data.isNotEmpty()) {
            Log.d(TAG, "$operation - Data length: ${data.size} bytes")
        }
    }
    
    // MARK: - Card Status Utilities
    
    /**
     * Get human-readable card status description
     */
    fun getCardStatusDescription(status: ApplicationStatus?): String {
        if (status == null) {
            return "Unknown"
        }
        
        return buildString {
            append("Seeded: ${status.isSeeded}")
            append(", Setup done: ${status.isSetupDone}")
            append(", Needs secure channel: ${status.needsSecureChannel()}")
            append(", PIN remaining: ${status.getPin0RemainingCounter()}")
            append(", PUK remaining: ${status.getPuk0RemainingCounter()}")
            append(", Card version: ${status.getCardVersionString()}")
        }
    }
    
    /**
     * Check if card is ready for operations
     */
    fun isCardReady(status: ApplicationStatus?): Boolean {
        return status?.isSeeded == true && status.isSetupDone == true
    }
    
    // MARK: - PIN Utilities
    
    /**
     * Validate PIN format
     */
    fun validatePin(pin: String): Boolean {
        return pin.length in 4..8 && pin.all { it.isDigit() }
    }
    
    /**
     * Convert PIN string to byte array
     */
    fun pinToBytes(pin: String): ByteArray {
        return pin.toByteArray(StandardCharsets.UTF_8)
    }
    
    /**
     * Convert byte array back to PIN string
     */
    fun bytesToPin(bytes: ByteArray): String {
        return String(bytes, StandardCharsets.UTF_8)
    }
    
    // MARK: - Keyslot Utilities
    
    /**
     * Validate keyslot number
     */
    fun validateKeyslot(keyslot: Int): Boolean {
        return keyslot in 0..15
    }
    
    /**
     * Get keyslot description
     */
    fun getKeyslotDescription(keyslot: Int): String {
        return when (keyslot) {
            0 -> "Default keyslot"
            in 1..15 -> "Keyslot $keyslot"
            else -> "Invalid keyslot"
        }
    }
    
    // MARK: - Error Handling
    
    /**
     * Create a user-friendly error message
     */
    fun createUserFriendlyError(operation: String, error: String): String {
        return "Failed to $operation: $error"
    }
    
    /**
     * Log error with context
     */
    fun logError(operation: String, error: String, throwable: Throwable? = null) {
        Log.e(TAG, "Error during $operation: $error", throwable)
    }
}
