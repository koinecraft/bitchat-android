package com.bitchat.android.satochip

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages Satochip PIN storage and validation with timeout functionality
 */
class SatochipPinManager {
    companion object {
        private const val TAG = "SatochipPinManager"
        private const val PIN_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes in milliseconds
        private const val MIN_PIN_LENGTH = 4
        private const val MAX_PIN_LENGTH = 32
    }
    
    @Volatile
    private var storedPin: String? = null
    
    @Volatile
    private var pinFirstUsedTime: AtomicLong = AtomicLong(0)
    
    /**
     * Check if PIN is currently valid (stored and not expired)
     */
    fun isPinValid(): Boolean {
        val pin = storedPin ?: return false
        val firstUsedTime = pinFirstUsedTime.get()
        
        if (firstUsedTime == 0L) {
            return false // PIN was never used
        }
        
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - firstUsedTime
        
        return timeElapsed < PIN_TIMEOUT_MS
    }
    
    /**
     * Get the stored PIN if it's still valid
     */
    fun getValidPin(): String? {
        return if (isPinValid()) storedPin else null
    }
    
    /**
     * Store a new PIN and record the time it was first used
     */
    fun storePin(pin: String) {
        if (!isValidPinFormat(pin)) {
            throw IllegalArgumentException("Invalid PIN format")
        }
        
        storedPin = pin
        pinFirstUsedTime.set(System.currentTimeMillis())
        Log.d(TAG, "PIN stored successfully, timeout set for ${PIN_TIMEOUT_MS / 1000 / 60} minutes")
    }
    
    /**
     * Clear the stored PIN
     */
    fun clearPin() {
        storedPin = null
        pinFirstUsedTime.set(0)
        Log.d(TAG, "PIN cleared")
    }
    
    /**
     * Get the remaining time before PIN expires (in milliseconds)
     */
    fun getRemainingTimeMs(): Long {
        val firstUsedTime = pinFirstUsedTime.get()
        if (firstUsedTime == 0L) return 0L
        
        val currentTime = System.currentTimeMillis()
        val timeElapsed = currentTime - firstUsedTime
        val remaining = PIN_TIMEOUT_MS - timeElapsed
        
        return if (remaining > 0) remaining else 0L
    }
    
    /**
     * Get the remaining time before PIN expires (in minutes)
     */
    fun getRemainingTimeMinutes(): Int {
        return (getRemainingTimeMs() / 1000 / 60).toInt()
    }
    
    /**
     * Check if PIN format is valid (alphanumeric, 4-32 characters)
     */
    fun isValidPinFormat(pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH || pin.length > MAX_PIN_LENGTH) {
            return false
        }
        
        // Allow alphanumeric characters (upper and lower case)
        return pin.all { it.isLetterOrDigit() }
    }
    
    /**
     * Get PIN validation error message if format is invalid
     */
    fun getPinValidationError(pin: String): String? {
        return when {
            pin.length < MIN_PIN_LENGTH -> "PIN must be at least $MIN_PIN_LENGTH characters"
            pin.length > MAX_PIN_LENGTH -> "PIN must be no more than $MAX_PIN_LENGTH characters"
            !pin.all { it.isLetterOrDigit() } -> "PIN can only contain letters and numbers"
            else -> null
        }
    }
    
    /**
     * Check if PIN has expired
     */
    fun isPinExpired(): Boolean {
        return !isPinValid()
    }
    
    /**
     * Get the timeout duration in minutes
     */
    fun getTimeoutMinutes(): Int {
        return (PIN_TIMEOUT_MS / 1000 / 60).toInt()
    }
}
