package com.bitchat.android.satochip

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.ui.ChatViewModel
import org.satochip.client.ApplicationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * High-level Satochip service that provides a clean API for the application
 * Manages Satochip card operations and integrates with the mesh network
 */
class SatochipService private constructor(
    private val context: Context,
    private val meshService: BluetoothMeshService,
    private val chatViewModel: ChatViewModel
) {
    
    companion object {
        private const val TAG = "SatochipService"
        
        @Volatile
        private var INSTANCE: SatochipService? = null
        
        fun getInstance(
            context: Context,
            meshService: BluetoothMeshService,
            chatViewModel: ChatViewModel
        ): SatochipService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SatochipService(
                    context.applicationContext,
                    meshService,
                    chatViewModel
                ).also { INSTANCE = it }
            }
        }
    }
    
    // Core components
    private val cardManager = SatochipCardManager(context, meshService, chatViewModel)
    private val pinManager = SatochipPinManager()
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Service state
    private var isInitialized = false
    
    init {
        setupCardManagerCallbacks()
    }
    
    /**
     * Initialize the Satochip service
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Satochip service already initialized")
            return
        }
        
        Log.d(TAG, "Initializing Satochip service")
        isInitialized = true
    }
    
    /**
     * Get the card manager for direct access
     */
    fun getCardManager(): SatochipCardManager {
        return cardManager
    }
    
    /**
     * Check if a Satochip card is connected
     */
    fun isCardConnected(): Boolean {
        return cardManager.isConnected()
    }
    
    /**
     * Get the current card status
     */
    fun getCardStatus(): ApplicationStatus? {
        return cardManager.getCardStatus()
    }
    
    /**
     * Sign a Nostr event using the connected Satochip card
     */
    fun signNostrEvent(eventContent: String, keyslot: Int = 0): String? {
        if (!isCardConnected()) {
            Log.w(TAG, "Cannot sign event: no card connected")
            return null
        }
        
        return try {
            // Hash the event content
            val eventHash = hashEventContent(eventContent)
            
            // Sign with the card
            val signature = cardManager.signNostrEvent(eventHash, keyslot)
            if (signature != null) {
                Log.d(TAG, "Successfully signed Nostr event with keyslot $keyslot")
                Log.d(TAG, "Event content: $eventContent")
                Log.d(TAG, "Signature: ${signature.joinToString("") { "%02x".format(it) }}")
                
                // Return the message with the actual signature appended
                val signatureHex = signature.joinToString("") { "%02x".format(it) }
                return "$eventContent:$signatureHex"
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error signing Nostr event: ${e.message}")
            null
        }
    }
    
    /**
     * Get public key for a specific keyslot
     */
    fun getPublicKey(keyslot: Int = 0): ByteArray? {
        return cardManager.getPublicKey(keyslot)
    }
    
    /**
     * Verify PIN with the card and store it if valid
     */
    fun verifyPin(pin: String): Boolean {
        // First verify the PIN format
        if (!pinManager.isValidPinFormat(pin)) {
            Log.w(TAG, "Invalid PIN format")
            return false
        }
        
        // Verify PIN with the card
        val isValid = cardManager.verifyPin(pin.toByteArray())
        
        if (isValid) {
            // Store the PIN for future use
            pinManager.storePin(pin)
            Log.d(TAG, "PIN verified and stored successfully")
        } else {
            Log.w(TAG, "PIN verification failed")
        }
        
        return isValid
    }
    
    /**
     * Check if we have a valid stored PIN
     */
    fun hasValidPin(): Boolean {
        return pinManager.isPinValid()
    }
    
    /**
     * Get the remaining time before PIN expires (in minutes)
     */
    fun getPinRemainingTimeMinutes(): Int {
        return pinManager.getRemainingTimeMinutes()
    }
    
    /**
     * Use stored PIN for card operations (if valid)
     */
    fun useStoredPin(): Boolean {
        val storedPin = pinManager.getValidPin()
        if (storedPin == null) {
            Log.w(TAG, "No valid stored PIN available")
            return false
        }
        
        // Verify the stored PIN is still valid with the card
        return cardManager.verifyPin(storedPin.toByteArray())
    }
    
    /**
     * Clear stored PIN
     */
    fun clearStoredPin() {
        pinManager.clearPin()
        Log.d(TAG, "Stored PIN cleared")
    }
    
    /**
     * Validate PIN format without verifying with card
     */
    fun validatePinFormat(pin: String): Boolean {
        return pinManager.isValidPinFormat(pin)
    }
    
    /**
     * Get PIN validation error message
     */
    fun getPinValidationError(pin: String): String? {
        return pinManager.getPinValidationError(pin)
    }
    
    /**
     * Initialize a new Satochip card
     */
    fun initializeCard(): Boolean {
        return cardManager.initializeCard()
    }
    
    /**
     * Verify card authenticity
     */
    fun verifyCardAuthenticity(): Boolean {
        return cardManager.verifyCardAuthenticity()
    }
    
    /**
     * Set up callbacks for card manager events
     */
    private fun setupCardManagerCallbacks() {
        cardManager.onCardConnected = {
            Log.d(TAG, "Card connected - updating UI state")
            scope.launch {
                // Update UI state to show card is connected
                // This could trigger UI updates in the chat view model
            }
        }
        
        cardManager.onCardDisconnected = {
            Log.d(TAG, "Card disconnected - updating UI state")
            scope.launch {
                // Update UI state to show card is disconnected
            }
        }
        
        cardManager.onCardError = { error ->
            Log.e(TAG, "Card error: $error")
            scope.launch {
                // Handle card errors - could show user notification
            }
        }
        
        cardManager.onCardStatusUpdated = { status ->
            Log.d(TAG, "Card status updated: $status")
            scope.launch {
                // Update UI with new card status
            }
        }
    }
    
    /**
     * Hash event content for signing
     */
    private fun hashEventContent(content: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray())
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up Satochip service")
        isInitialized = false
    }
}
