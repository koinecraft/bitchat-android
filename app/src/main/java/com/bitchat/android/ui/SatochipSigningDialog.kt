package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.satochip.SatochipService
import kotlinx.coroutines.launch

/**
 * Satochip signing dialog that handles the complete NFC signing flow
 */
@Composable
fun SatochipSigningDialog(
    messageContent: String,
    onSigningComplete: (String) -> Unit,
    onSigningCancelled: () -> Unit,
    satochipService: SatochipService
) {
    var currentStep by remember { mutableStateOf(SigningStep.CHECKING_CARD) }
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var signedMessage by remember { mutableStateOf<String?>(null) }
    
    val scope = rememberCoroutineScope()
    
    // Check card status when dialog opens
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                if (!satochipService.isCardConnected()) {
                    currentStep = SigningStep.NO_CARD
                    return@launch
                }
                
                val status = satochipService.getCardStatus()
                if (status == null) {
                    currentStep = SigningStep.NO_CARD
                    return@launch
                }
                
                if (status.isSeeded && status.isSetupDone) {
                    // Check if we have a valid stored PIN
                    if (satochipService.hasValidPin()) {
                        // Show that we're using stored PIN
                        currentStep = SigningStep.USING_STORED_PIN
                        
                                                    // Try to use stored PIN
                            if (satochipService.useStoredPin()) {
                                currentStep = SigningStep.SIGNING
                                // Proceed directly to signing
                                val signedMessageContent = satochipService.signNostrEvent(messageContent)
                                if (signedMessageContent != null) {
                                    signedMessage = signedMessageContent
                                    currentStep = SigningStep.COMPLETE
                                } else {
                                    errorMessage = "Failed to sign message"
                                    currentStep = SigningStep.ERROR
                                }
                            } else {
                            // Stored PIN is invalid, need to re-enter
                            currentStep = SigningStep.PIN_VERIFICATION
                        }
                    } else {
                        // No stored PIN, need to enter one
                        currentStep = SigningStep.PIN_VERIFICATION
                    }
                } else {
                    currentStep = SigningStep.CARD_SETUP_REQUIRED
                }
            } catch (e: Exception) {
                errorMessage = "Error checking card: ${e.message}"
                currentStep = SigningStep.ERROR
            }
        }
    }
    
    Dialog(
        onDismissRequest = { 
            if (!isProcessing) onSigningCancelled() 
        },
        properties = DialogProperties(
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                when (currentStep) {
                    SigningStep.CHECKING_CARD -> {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Checking Satochip Card",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    SigningStep.NO_CARD -> {
                        Icon(
                            imageVector = Icons.Filled.Nfc,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Satochip Card Detected",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please place your Satochip card near the NFC reader and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { 
                                currentStep = SigningStep.CHECKING_CARD
                                errorMessage = null
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                    
                    SigningStep.CARD_SETUP_REQUIRED -> {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Card Setup Required",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your Satochip card needs to be initialized before signing messages.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isProcessing = true
                                    try {
                                        val success = satochipService.initializeCard()
                                        if (success) {
                                            currentStep = SigningStep.PIN_VERIFICATION
                                        } else {
                                            errorMessage = "Failed to initialize card"
                                            currentStep = SigningStep.ERROR
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "Error initializing card: ${e.message}"
                                        currentStep = SigningStep.ERROR
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Initialize Card")
                        }
                    }
                    
                    SigningStep.PIN_VERIFICATION -> {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Enter PIN",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter your Satochip PIN (alphanumeric, 4-32 characters).",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it },
                            label = { Text("PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = KeyboardType.Text
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSigningCancelled,
                                modifier = Modifier.weight(1f),
                                enabled = !isProcessing
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isProcessing = true
                                        try {
                                            // First validate PIN format
                                            val validationError = satochipService.getPinValidationError(pinInput)
                                            if (validationError != null) {
                                                errorMessage = validationError
                                                pinInput = ""
                                                isProcessing = false
                                                return@launch
                                            }
                                            
                                            val success = satochipService.verifyPin(pinInput)
                                            if (success) {
                                                currentStep = SigningStep.SIGNING
                                                // Proceed to signing
                                                val signedMessageContent = satochipService.signNostrEvent(messageContent)
                                                if (signedMessageContent != null) {
                                                    signedMessage = signedMessageContent
                                                    currentStep = SigningStep.COMPLETE
                                                } else {
                                                    errorMessage = "Failed to sign message"
                                                    currentStep = SigningStep.ERROR
                                                }
                                            } else {
                                                errorMessage = "Invalid PIN"
                                                pinInput = ""
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Error verifying PIN: ${e.message}"
                                            currentStep = SigningStep.ERROR
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = pinInput.isNotEmpty() && !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Verify PIN")
                            }
                        }
                    }
                    
                    SigningStep.USING_STORED_PIN -> {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Using Stored PIN",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Using your stored PIN (expires in ${satochipService.getPinRemainingTimeMinutes()} minutes).",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    SigningStep.SIGNING -> {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Signing Message",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please keep your Satochip card near the NFC reader while signing.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    SigningStep.COMPLETE -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Message Signed Successfully",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your message has been cryptographically signed with your Satochip card.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                signedMessage?.let { onSigningComplete(it) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Send Signed Message")
                        }
                    }
                    
                    SigningStep.ERROR -> {
                        Icon(
                            imageVector = Icons.Filled.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color(0xFFF44336)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Signing Failed",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "An unknown error occurred",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSigningCancelled,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    currentStep = SigningStep.CHECKING_CARD
                                    errorMessage = null
                                    pinInput = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retry")
                            }
                        }
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
