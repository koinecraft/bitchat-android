# Satochip Nostr Signing Integration Guide for Android

## Overview

This guide provides a complete implementation for integrating Satochip hardware wallet functionality into an Android application for Nostr event signing. The integration enables users to sign Nostr events using a physical Satochip card via NFC, providing enhanced security for decentralized messaging applications.

## Prerequisites

- Android project with minimum SDK 21+
- NFC-enabled Android device
- Satochip hardware wallet card
- Gradle build system

## Required Dependencies

### 1. Add Satochip Libraries

Place the following JAR files in your `app/libs/` directory:
- `satochip-android-0.0.2.jar`
- `satochip-lib-0.2.6.2.jar`

### 2. Update build.gradle.kts

```kotlin
android {
    // ... existing config ...
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Exclude conflicting BouncyCastle classes
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages_de.properties"
            excludes += "org/bouncycastle/x509/CertPathReviewerMessages.properties"
            excludes += "org/bouncycastle/**"
        }
    }
}

dependencies {
    // Exclude conflicting BouncyCastle versions
    configurations.all {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    
    // Satochip libraries
    implementation(files("libs/satochip-lib-0.2.6.2.jar"))
    implementation(files("libs/satochip-android-0.0.2.jar"))
    
    // Satochip-related dependencies
    implementation("org.bitcoinj:bitcoinj-core:0.16.2") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }
    
    // BouncyCastle for cryptography
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
}
```

### 3. Update AndroidManifest.xml

```xml
<manifest>
    <!-- NFC permissions -->
    <uses-permission android:name="android.permission.NFC" />
    
    <!-- Hardware features -->
    <uses-feature android:name="android.hardware.nfc" android:required="false" />
    
    <!-- ... rest of manifest ... -->
</manifest>
```

## Core Implementation

### 1. SatochipCardManager.kt

```kotlin
package com.yourpackage.satochip

import android.util.Log
import org.satochip.io.CardListener
import org.satochip.client.SatochipCommandSet
import org.satochip.client.ApplicationStatus
import org.satochip.io.APDUResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SatochipCardManager : CardListener {
    private val TAG = "SatochipCardManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private var currentCommandSet: SatochipCommandSet? = null
    private var cardStatus: ApplicationStatus? = null
    
    override fun onConnected() {
        Log.d(TAG, "✅ Satochip card connected")
        scope.launch {
            try {
                initializeCardConnection()
            } catch (e: Exception) {
                Log.e(TAG, "Error during card connection: ${e.message}")
            }
        }
    }
    
    override fun onDisconnected() {
        Log.d(TAG, "❌ Satochip card disconnected")
        currentCommandSet = null
        cardStatus = null
    }
    
    private suspend fun initializeCardConnection() {
        val commandSet = currentCommandSet ?: return
        
        // Select Satochip applet
        val response = commandSet.cardSelect()
        if (!SatochipUtils.validateResponse(response)) {
            throw Exception("Failed to select Satochip applet")
        }
        
        // Get card status
        val status = commandSet.cardGetStatus()
        if (!SatochipUtils.validateResponse(status)) {
            throw Exception("Failed to get card status")
        }
        
        cardStatus = status.getData()
        Log.d(TAG, "Card status: setup_done: ${cardStatus?.isSetupDone}")
        Log.d(TAG, "is_seeded: ${cardStatus?.isSeeded}")
        Log.d(TAG, "needs_2FA: ${cardStatus?.needs2FA}")
        Log.d(TAG, "needs_secure_channel: ${cardStatus?.needsSecureChannel()}")
    }
    
    fun verifyPin(pin: String): Boolean {
        val commandSet = currentCommandSet ?: return false
        
        return try {
            val response = commandSet.cardVerifyPIN(pin.toByteArray())
            SatochipUtils.validateResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "PIN verification failed: ${e.message}")
            false
        }
    }
    
    fun signNostrEvent(eventContent: String): String? {
        val commandSet = currentCommandSet ?: return null
        
        return try {
            // Hash the message content
            val messageHash = hashMessageContent(eventContent)
            
            // Sign with BIP-340 Schnorr
            val response = commandSet.cardSignSchnorrHash(messageHash, 0)
            if (!SatochipUtils.validateResponse(response)) {
                throw Exception("Signing failed")
            }
            
            val signature = response.getData()
            val signatureHex = signature.joinToString("") { "%02x".format(it) }
            
            Log.d(TAG, "Successfully signed Nostr event with keyslot 0")
            Log.d(TAG, "Event content: $eventContent")
            Log.d(TAG, "Signature: $signatureHex")
            
            // Return content with signature
            "$eventContent~$signatureHex"
        } catch (e: Exception) {
            Log.e(TAG, "Error signing Nostr event: ${e.message}")
            null
        }
    }
    
    fun getPublicKey(): String? {
        val commandSet = currentCommandSet ?: return null
        
        return try {
            val response = commandSet.cardGetPubkeyFromKeyslot(0)
            if (!SatochipUtils.validateResponse(response)) {
                throw Exception("Failed to get public key")
            }
            
            val pubkey = response.getData()
            pubkey.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting public key: ${e.message}")
            null
        }
    }
    
    private fun hashMessageContent(messageContent: String): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(messageContent.toByteArray(Charsets.UTF_8))
    }
    
    fun isCardConnected(): Boolean = currentCommandSet != null
    fun getCardStatus(): ApplicationStatus? = cardStatus
}
```

### 2. SatochipPinManager.kt

```kotlin
package com.yourpackage.satochip

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SatochipPinManager(private val context: Context) {
    private val TAG = "SatochipPinManager"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val prefs: SharedPreferences = context.getSharedPreferences("satochip_pin", Context.MODE_PRIVATE)
    private val PIN_TIMEOUT_MINUTES = 10L
    
    private var storedPin: String? = null
    private var pinExpiryTime: Long = 0
    
    init {
        loadStoredPin()
    }
    
    fun storePin(pin: String) {
        storedPin = pin
        pinExpiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(PIN_TIMEOUT_MINUTES)
        saveStoredPin()
        
        Log.d(TAG, "PIN stored with ${PIN_TIMEOUT_MINUTES} minute timeout")
        
        // Auto-clear after timeout
        scope.launch {
            delay(TimeUnit.MINUTES.toMillis(PIN_TIMEOUT_MINUTES))
            if (System.currentTimeMillis() >= pinExpiryTime) {
                clearStoredPin()
                Log.d(TAG, "PIN automatically cleared after timeout")
            }
        }
    }
    
    fun getStoredPin(): String? {
        if (System.currentTimeMillis() >= pinExpiryTime) {
            clearStoredPin()
            return null
        }
        return storedPin
    }
    
    fun hasValidPin(): Boolean = getStoredPin() != null
    
    fun getPinRemainingTimeMinutes(): Long {
        if (pinExpiryTime <= System.currentTimeMillis()) return 0
        return TimeUnit.MILLISECONDS.toMinutes(pinExpiryTime - System.currentTimeMillis())
    }
    
    fun clearStoredPin() {
        storedPin = null
        pinExpiryTime = 0
        saveStoredPin()
        Log.d(TAG, "Stored PIN cleared")
    }
    
    fun validatePinFormat(pin: String): Boolean {
        // Satochip PINs are 4-8 alphanumeric characters
        return pin.length in 4..8 && pin.all { it.isLetterOrDigit() }
    }
    
    fun getPinValidationError(pin: String): String? {
        return when {
            pin.isEmpty() -> "PIN cannot be empty"
            pin.length < 4 -> "PIN must be at least 4 characters"
            pin.length > 8 -> "PIN cannot exceed 8 characters"
            !pin.all { it.isLetterOrDigit() } -> "PIN can only contain letters and numbers"
            else -> null
        }
    }
    
    private fun loadStoredPin() {
        storedPin = prefs.getString("stored_pin", null)
        pinExpiryTime = prefs.getLong("pin_expiry", 0)
        
        // Clear if expired
        if (System.currentTimeMillis() >= pinExpiryTime) {
            clearStoredPin()
        }
    }
    
    private fun saveStoredPin() {
        prefs.edit().apply {
            putString("stored_pin", storedPin)
            putLong("pin_expiry", pinExpiryTime)
            apply()
        }
    }
}
```

### 3. SatochipService.kt

```kotlin
package com.yourpackage.satochip

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SatochipService private constructor(
    private val context: Context,
    private val cardManager: SatochipCardManager,
    private val pinManager: SatochipPinManager
) {
    private val TAG = "SatochipService"
    
    private val _cardConnected = MutableStateFlow(false)
    val cardConnected: StateFlow<Boolean> = _cardConnected
    
    private val _cardStatus = MutableStateFlow<ApplicationStatus?>(null)
    val cardStatus: StateFlow<ApplicationStatus?> = _cardStatus
    
    private val _pinVerified = MutableStateFlow(false)
    val pinVerified: StateFlow<Boolean> = _pinVerified
    
    companion object {
        @Volatile
        private var INSTANCE: SatochipService? = null
        
        fun getInstance(context: Context): SatochipService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SatochipService(
                    context,
                    SatochipCardManager(),
                    SatochipPinManager(context)
                ).also { INSTANCE = it }
            }
        }
    }
    
    fun initialize() {
        Log.d(TAG, "Initializing Satochip service")
        // Card manager will be connected via NFC
    }
    
    fun verifyPin(pin: String): Boolean {
        if (!pinManager.validatePinFormat(pin)) {
            Log.w(TAG, "Invalid PIN format")
            return false
        }
        
        val isValid = cardManager.verifyPin(pin)
        if (isValid) {
            pinManager.storePin(pin)
            _pinVerified.value = true
            Log.d(TAG, "PIN verified and stored successfully")
        }
        
        return isValid
    }
    
    fun signNostrEvent(eventContent: String): String? {
        // Check if we have a valid stored PIN
        val storedPin = pinManager.getStoredPin()
        if (storedPin != null) {
            // Re-verify stored PIN
            if (cardManager.verifyPin(storedPin)) {
                Log.d(TAG, "Using stored PIN for signing")
            } else {
                pinManager.clearStoredPin()
                _pinVerified.value = false
                Log.w(TAG, "Stored PIN verification failed")
                return null
            }
        } else {
            Log.w(TAG, "No valid PIN available for signing")
            return null
        }
        
        return cardManager.signNostrEvent(eventContent)
    }
    
    fun getPublicKey(): String? = cardManager.getPublicKey()
    
    fun hasValidPin(): Boolean = pinManager.hasValidPin()
    
    fun getPinRemainingTimeMinutes(): Long = pinManager.getPinRemainingTimeMinutes()
    
    fun clearStoredPin() {
        pinManager.clearStoredPin()
        _pinVerified.value = false
    }
    
    fun validatePinFormat(pin: String): Boolean = pinManager.validatePinFormat(pin)
    
    fun getPinValidationError(pin: String): String? = pinManager.getPinValidationError(pin)
    
    fun getCardManager(): SatochipCardManager = cardManager
    
    fun isSatochipReady(): Boolean {
        return cardManager.isCardConnected() && 
               cardManager.getCardStatus()?.isSetupDone == true &&
               hasValidPin()
    }
}
```

### 4. SatochipMessageParser.kt

```kotlin
package com.yourpackage.satochip

import android.util.Log

object SatochipMessageParser {
    private const val TAG = "SatochipMessageParser"
    private const val SIGNATURE_SEPARATOR = "~"
    
    fun parseMessage(messageContent: String): ParsedMessage {
        val lastSeparatorIndex = messageContent.lastIndexOf(SIGNATURE_SEPARATOR)
        
        return if (lastSeparatorIndex != -1 && lastSeparatorIndex < messageContent.length - 1) {
            val content = messageContent.substring(0, lastSeparatorIndex)
            val signature = messageContent.substring(lastSeparatorIndex + 1)
            
            Log.d(TAG, "Parsed signed message - Content: '$content', Signature: '$signature'")
            
            ParsedMessage(
                content = content,
                signature = signature,
                isSigned = true
            )
        } else {
            ParsedMessage(
                content = messageContent,
                signature = null,
                isSigned = false
            )
        }
    }
    
    fun isSignedMessage(messageContent: String): Boolean {
        val lastSeparatorIndex = messageContent.lastIndexOf(SIGNATURE_SEPARATOR)
        return lastSeparatorIndex != -1 && lastSeparatorIndex < messageContent.length - 1
    }
    
    fun getDisplayContent(parsedMessage: ParsedMessage): String {
        return if (parsedMessage.isSigned) {
            "${parsedMessage.content}~signed"
        } else {
            parsedMessage.content
        }
    }
    
    fun verifySignedMessage(messageContent: String, signatureHex: String, publicKeyHex: String): Boolean {
        return try {
            val messageHash = hashMessageContent(messageContent)
            // Use your existing NostrCrypto or similar library
            val isValid = NostrCrypto.schnorrVerify(messageHash, signatureHex, publicKeyHex)
            Log.d(TAG, "Signature verification result: $isValid for message: '$messageContent'")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying signature: ${e.message}")
            false
        }
    }
    
    private fun hashMessageContent(messageContent: String): ByteArray {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(messageContent.toByteArray(Charsets.UTF_8))
    }
}

data class ParsedMessage(
    val content: String,
    val signature: String?,
    val isSigned: Boolean
)
```

### 5. SatochipUtils.kt

```kotlin
package com.yourpackage.satochip

import android.util.Log
import org.satochip.io.APDUResponse
import org.satochip.client.ApplicationStatus

object SatochipUtils {
    private const val TAG = "SatochipUtils"
    
    // Status word constants
    const val SW_OK = 0x9000
    const val SW_WRONG_PIN = 0x6982
    const val SW_PIN_BLOCKED = 0x6983
    const val SW_CARD_NOT_SETUP = 0x6984
    const val SW_CARD_NOT_SEEDED = 0x6985
    
    fun validateResponse(response: APDUResponse): Boolean {
        val statusWord = response.getSw()
        val isValid = statusWord == SW_OK
        
        if (!isValid) {
            Log.w(TAG, "APDU response error: ${getStatusWordDescription(statusWord)}")
        }
        
        return isValid
    }
    
    fun getStatusWordDescription(statusWord: Int): String {
        return when (statusWord) {
            SW_OK -> "OK"
            SW_WRONG_PIN -> "Wrong PIN"
            SW_PIN_BLOCKED -> "PIN blocked"
            SW_CARD_NOT_SETUP -> "Card not setup"
            SW_CARD_NOT_SEEDED -> "Card not seeded"
            else -> "Unknown error: 0x${statusWord.toString(16)}"
        }
    }
    
    fun isCardReady(status: ApplicationStatus?): Boolean {
        return status?.let { 
            it.isSeeded && it.isSetupDone && !it.needsSecureChannel()
        } ?: false
    }
    
    fun getCardStatusDescription(status: ApplicationStatus?): String {
        return status?.let {
            "Setup: ${if (it.isSetupDone) "Done" else "Required"}, " +
            "Seeded: ${if (it.isSeeded) "Yes" else "No"}, " +
            "Secure Channel: ${if (it.needsSecureChannel()) "Required" else "Not Required"}"
        } ?: "Unknown"
    }
}
```

## UI Components

### 1. SatochipSigningDialog.kt

```kotlin
package com.yourpackage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SatochipSigningDialog(
    messageContent: String,
    satochipService: SatochipService,
    onSigningComplete: (String) -> Unit,
    onSigningCancelled: () -> Unit
) {
    var currentStep by remember { mutableStateOf(SigningStep.CHECKING_CARD) }
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var signedMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(Unit) {
        // Check if we have a valid stored PIN
        if (satochipService.hasValidPin()) {
            currentStep = SigningStep.USING_STORED_PIN
            delay(1000) // Show briefly
        }
        
        // Check card status
        if (satochipService.getCardManager().isCardConnected()) {
            currentStep = SigningStep.PIN_VERIFICATION
        } else {
            currentStep = SigningStep.NO_CARD
        }
    }
    
    Dialog(onDismissRequest = onSigningCancelled) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (currentStep) {
                        SigningStep.CHECKING_CARD -> "Checking Satochip Card..."
                        SigningStep.NO_CARD -> "No Satochip Card Detected"
                        SigningStep.CARD_SETUP_REQUIRED -> "Card Setup Required"
                        SigningStep.PIN_VERIFICATION -> "Enter PIN"
                        SigningStep.USING_STORED_PIN -> "Using Stored PIN..."
                        SigningStep.SIGNING -> "Signing Message..."
                        SigningStep.COMPLETE -> "Message Signed Successfully!"
                        SigningStep.ERROR -> "Error: $errorMessage"
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                when (currentStep) {
                    SigningStep.PIN_VERIFICATION -> {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("PIN") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row {
                            Button(
                                onClick = {
                                    val error = satochipService.getPinValidationError(pin)
                                    if (error != null) {
                                        errorMessage = error
                                        currentStep = SigningStep.ERROR
                                    } else {
                                        currentStep = SigningStep.SIGNING
                                        // Verify PIN and sign
                                        if (satochipService.verifyPin(pin)) {
                                            signedMessage = satochipService.signNostrEvent(messageContent)
                                            if (signedMessage != null) {
                                                currentStep = SigningStep.COMPLETE
                                            } else {
                                                errorMessage = "Signing failed"
                                                currentStep = SigningStep.ERROR
                                            }
                                        } else {
                                            errorMessage = "Invalid PIN"
                                            currentStep = SigningStep.ERROR
                                        }
                                    }
                                }
                            ) {
                                Text("Sign Message")
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Button(
                                onClick = onSigningCancelled
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                    
                    SigningStep.COMPLETE -> {
                        Button(
                            onClick = {
                                signedMessage?.let { onSigningComplete(it) }
                            }
                        ) {
                            Text("Send Signed Message")
                        }
                    }
                    
                    SigningStep.ERROR -> {
                        Button(
                            onClick = {
                                errorMessage = null
                                currentStep = SigningStep.PIN_VERIFICATION
                            }
                        ) {
                            Text("Try Again")
                        }
                    }
                    
                    else -> {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

enum class SigningStep {
    CHECKING_CARD,
    NO_CARD,
    CARD_SETUP_REQUIRED,
    PIN_VERIFICATION,
    USING_STORED_PIN,
    SIGNING,
    COMPLETE,
    ERROR
}
```

## Integration Steps

### 1. Initialize in MainActivity

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var satochipService: SatochipService
    private lateinit var nfcCardManager: NFCCardManager
    private var nfcAdapter: NfcAdapter? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Satochip
        initializeSatochip()
        
        // ... rest of onCreate
    }
    
    private fun initializeSatochip() {
        try {
            // Initialize Satochip service
            satochipService = SatochipService.getInstance(this)
            
            // Initialize NFC card manager
            nfcCardManager = NFCCardManager()
            nfcCardManager.setCardListener(satochipService.getCardManager() as CardListener)
            nfcCardManager.start()
            
            // Enable NFC reader mode
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            nfcAdapter?.let { adapter ->
                if (adapter.isEnabled) {
                    adapter.enableReaderMode(
                        this,
                        nfcCardManager,
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
                        null
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing Satochip: ${e.message}")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup Satochip
        nfcAdapter?.disableReaderMode(this)
        nfcCardManager.stop()
    }
}
```

### 2. Add to ViewModel

```kotlin
class ChatViewModel : ViewModel() {
    private val satochipService = SatochipService.getInstance(context)
    
    fun signNostrEventWithSatochip(eventContent: String): String? {
        return satochipService.signNostrEvent(eventContent)
    }
    
    fun isSatochipCardConnected(): Boolean {
        return satochipService.getCardManager().isCardConnected()
    }
    
    fun isSatochipReady(): Boolean {
        return satochipService.isSatochipReady()
    }
    
    fun verifySatochipPin(pin: String): Boolean {
        return satochipService.verifyPin(pin)
    }
}
```

### 3. Message Handling

```kotlin
// In your message handler
fun handleIncomingMessage(messageContent: String) {
    val parsedMessage = SatochipMessageParser.parseMessage(messageContent)
    
    // Display message with ~signed indicator if signed
    val displayContent = SatochipMessageParser.getDisplayContent(parsedMessage)
    
    // Verify signature if present
    if (parsedMessage.isSigned && parsedMessage.signature != null) {
        val isValid = SatochipMessageParser.verifySignedMessage(
            parsedMessage.content,
            parsedMessage.signature,
            senderPublicKey
        )
        Log.d(TAG, "Message signature verification: $isValid")
    }
    
    // Display the message
    displayMessage(displayContent)
}
```

## Usage Examples

### 1. Sign a Message

```kotlin
// In your UI
val signedMessage = satochipService.signNostrEvent("Hello, world!")
if (signedMessage != null) {
    // Send the signed message
    sendMessage(signedMessage)
} else {
    // Handle signing failure
    showError("Failed to sign message")
}
```

### 2. Verify a Received Message

```kotlin
val parsedMessage = SatochipMessageParser.parseMessage(receivedMessage)
if (parsedMessage.isSigned) {
    val isValid = SatochipMessageParser.verifySignedMessage(
        parsedMessage.content,
        parsedMessage.signature!!,
        senderPublicKey
    )
    if (isValid) {
        Log.d(TAG, "Message signature verified successfully")
    } else {
        Log.w(TAG, "Message signature verification failed")
    }
}
```

## Testing

### 1. Unit Tests

```kotlin
@Test
fun testMessageParsing() {
    val signedMessage = "Hello world~abc123def456"
    val parsed = SatochipMessageParser.parseMessage(signedMessage)
    
    assertEquals("Hello world", parsed.content)
    assertEquals("abc123def456", parsed.signature)
    assertTrue(parsed.isSigned)
}

@Test
fun testDisplayContent() {
    val parsed = ParsedMessage("Hello", "signature", true)
    val display = SatochipMessageParser.getDisplayContent(parsed)
    assertEquals("Hello~signed", display)
}
```

### 2. Integration Testing

```kotlin
@Test
fun testSatochipSigning() {
    // Mock Satochip card
    val satochipService = SatochipService.getInstance(context)
    
    // Test PIN verification
    val pinValid = satochipService.verifyPin("1234")
    assertTrue(pinValid)
    
    // Test message signing
    val signedMessage = satochipService.signNostrEvent("Test message")
    assertNotNull(signedMessage)
    assertTrue(signedMessage!!.contains("~"))
}
```

## Troubleshooting

### Common Issues

1. **BouncyCastle Conflicts**: Ensure all BouncyCastle exclusions are properly configured
2. **NFC Not Available**: Check device NFC capabilities and permissions
3. **Card Not Detected**: Ensure Satochip card is properly positioned on device
4. **PIN Verification Fails**: Verify PIN format and card setup status

### Debug Logging

```kotlin
// Enable debug logging
Log.d("SatochipService", "Card connected: ${cardManager.isCardConnected()}")
Log.d("SatochipService", "Card status: ${cardManager.getCardStatus()}")
Log.d("SatochipService", "PIN verified: ${satochipService.hasValidPin()}")
```

## Security Considerations

1. **PIN Storage**: PINs are stored in memory with timeout, not persistent storage
2. **Signature Verification**: Always verify signatures on received messages
3. **Card Authentication**: Verify card authenticity before use
4. **Secure Communication**: Use secure channels when available

## Conclusion

This integration provides a complete Satochip hardware wallet solution for Nostr event signing in Android applications. The implementation includes:

- NFC card management
- PIN verification and management
- Message signing and verification
- UI components for user interaction
- Comprehensive error handling

The modular design allows for easy integration into existing Android projects while maintaining security best practices.
