package com.bitchat.android.satochip

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.nostr.NostrClient
import org.satochip.io.CardListener
import org.satochip.android.NFCCardChannel
import org.satochip.client.SatochipCommandSet
import org.satochip.io.APDUCommand
import org.satochip.io.APDUResponse
import org.satochip.client.ApplicationStatus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

/**
 * Main Satochip card manager that implements CardListener interface
 * Handles NFC card connection events and manages Satochip operations
 */
class SatochipCardManager(
    private val context: Context,
    private val meshService: BluetoothMeshService,
    private val chatViewModel: ChatViewModel
) : CardListener {
    
    companion object {
        private const val TAG = "SatochipCardManager"
        private const val DEFAULT_PIN = "123456"
        private const val DEFAULT_KEYSLOT = 0
        private const val DEFAULT_PIN_TIMEOUT = 30
    }
    
    // Coroutines
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Card state
    private var isCardConnected = false
    private var currentCommandSet: SatochipCommandSet? = null
    private var currentCardStatus: ApplicationStatus? = null
    
    // Callbacks
    var onCardConnected: (() -> Unit)? = null
    var onCardDisconnected: (() -> Unit)? = null
    var onCardError: ((String) -> Unit)? = null
    var onCardStatusUpdated: ((ApplicationStatus) -> Unit)? = null
    
    // MARK: - CardListener Implementation
    
    override fun onConnected(channel: org.satochip.io.CardChannel) {
        Log.d(TAG, "✅ Satochip card connected")
        isCardConnected = true
        
        scope.launch {
            try {
                // Create command set for this connection
                val commandSet = SatochipCommandSet(channel)
                currentCommandSet = commandSet
                
                // Select the Satochip applet
                val selectResponse = commandSet.cardSelect("satochip")
                if (!selectResponse.isOK()) {
                    throw Exception("Failed to select Satochip applet: ${selectResponse.getSw()}")
                }
                
                // Get card status
                val statusResponse = commandSet.cardGetStatus()
                if (statusResponse.isOK()) {
                    val status = commandSet.getApplicationStatus()
                    currentCardStatus = status
                    Log.d(TAG, "Card status: $status")
                    onCardStatusUpdated?.invoke(status)
                }
                
                // Note: PIN verification will be handled by the signing dialog when needed
                // Don't auto-verify PIN on connection
                
                onCardConnected?.invoke()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during card connection: ${e.message}")
                onCardError?.invoke("Card connection failed: ${e.message}")
                isCardConnected = false
            }
        }
    }
    
    override fun onDisconnected() {
        Log.d(TAG, "❌ Satochip card disconnected")
        isCardConnected = false
        currentCommandSet = null
        currentCardStatus = null
        onCardDisconnected?.invoke()
    }
    
    // MARK: - Card Operations
    
    /**
     * Verify PIN with the card
     */
    fun verifyPin(pin: ByteArray): Boolean {
        if (!isCardConnected || currentCommandSet == null) {
            Log.w(TAG, "Cannot verify PIN: card not connected")
            return false
        }
        
        return try {
            val commandSet = currentCommandSet!!
            val response = commandSet.cardVerifyPIN(pin)
            if (response.isOK()) {
                Log.d(TAG, "PIN verification successful")
                true
            } else {
                Log.w(TAG, "PIN verification failed: ${response.getSw()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying PIN: ${e.message}")
            false
        }
    }
    
    /**
     * Sign a Nostr event using the Satochip card
     */
    fun signNostrEvent(eventHash: ByteArray, keyslot: Int = DEFAULT_KEYSLOT): ByteArray? {
        if (!isCardConnected || currentCommandSet == null) {
            Log.w(TAG, "Cannot sign event: card not connected")
            return null
        }
        
        return try {
            // Use the specified keyslot for signing
            val commandSet = currentCommandSet!!
            val response = commandSet.cardSignSchnorrHash(0xFF.toByte(), eventHash, null)
            if (response.isOK()) {
                val signature = response.getData()
                Log.d(TAG, "Event signed successfully with keyslot $keyslot")
                signature
            } else {
                Log.w(TAG, "Event signing failed: ${response.getSw()}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error signing event: ${e.message}")
            null
        }
    }
    
    /**
     * Get card status
     */
    fun getCardStatus(): ApplicationStatus? {
        return currentCardStatus
    }
    
    /**
     * Check if card is connected
     */
    fun isConnected(): Boolean {
        return isCardConnected
    }
    
    /**
     * Initialize card with default settings
     */
    fun initializeCard(): Boolean {
        if (!isCardConnected || currentCommandSet == null) {
            Log.w(TAG, "Cannot initialize card: card not connected")
            return false
        }
        
        return try {
            val commandSet = currentCommandSet!!
            val response = commandSet.cardSetup(0x00, DEFAULT_PIN.toByteArray(StandardCharsets.UTF_8))
            if (response.isOK()) {
                Log.d(TAG, "Card initialized successfully")
                true
            } else {
                Log.w(TAG, "Card initialization failed: ${response.getSw()}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing card: ${e.message}")
            false
        }
    }
    
    /**
     * Get public key for a specific keyslot
     */
    fun getPublicKey(keyslot: Int = DEFAULT_KEYSLOT): ByteArray? {
        if (!isCardConnected || currentCommandSet == null) {
            Log.w(TAG, "Cannot get public key: card not connected")
            return null
        }
        
        return try {
            val commandSet = currentCommandSet!!
            val publicKey = commandSet.cardGetPubkeyFromKeyslot(keyslot, false)
            if (publicKey.isNotEmpty()) {
                Log.d(TAG, "Retrieved public key for keyslot $keyslot")
                publicKey
            } else {
                Log.w(TAG, "Failed to get public key: empty response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public key: ${e.message}")
            null
        }
    }
    
    /**
     * Verify card authenticity
     */
    fun verifyCardAuthenticity(): Boolean {
        if (!isCardConnected || currentCommandSet == null) {
            Log.w(TAG, "Cannot verify authenticity: card not connected")
            return false
        }
        
        return try {
            val commandSet = currentCommandSet!!
            val result = commandSet.cardVerifyAuthenticity()
            if (result.isNotEmpty()) {
                Log.d(TAG, "Card authenticity verified")
                true
            } else {
                Log.w(TAG, "Card authenticity verification failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying card authenticity: ${e.message}")
            false
        }
    }
}
