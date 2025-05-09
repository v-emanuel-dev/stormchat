package com.ivip.brainstormia.billing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivip.brainstormia.R
import com.ivip.brainstormia.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Composable
fun PaymentScreen(
    onNavigateBack: () -> Unit,
    onPurchaseComplete: () -> Unit,
    isDarkTheme: Boolean
) {
    // Create a local view model instance
    val paymentViewModel: PaymentViewModel = viewModel()

    // Track payment success to trigger navigation
    val paymentState by paymentViewModel.paymentState.collectAsState()

    // Navigate on payment success
    LaunchedEffect(paymentState) {
        if (paymentState is PaymentState.Success) {
            delay(1500) // Give user time to see success message
            onPurchaseComplete()
        }
    }

    // Main payment UI implementation
    PaymentScreenContent(
        onBackToHome = onNavigateBack,
        paymentViewModel = paymentViewModel,
        isDarkTheme = isDarkTheme
    )
}

@Composable
private fun PaymentScreenContent(
    onBackToHome: () -> Unit,
    paymentViewModel: PaymentViewModel,
    isDarkTheme: Boolean
) {
    var cardNumber by remember { mutableStateOf("") }
    var cardholderName by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Track payment state
    val paymentState by paymentViewModel.paymentState.collectAsState()

    // Theme-specific colors
    val backgroundColor = if (isDarkTheme) BackgroundColorDark else BackgroundColor
    val cardColor = if (isDarkTheme) Color(0xFF121212) else Color.White
    val textColor = if (isDarkTheme) TextColorLight else TextColorDark
    val textSecondaryColor = if (isDarkTheme) TextColorLight.copy(alpha = 0.7f) else TextColorDark.copy(alpha = 0.9f)
    val inputBgColor = if (isDarkTheme) Color(0xFF212121) else Color.White
    val borderColor = if (isDarkTheme) Color.Gray.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.5f)
    val cardElevation = if (isDarkTheme) 8.dp else 4.dp

    // Handle payment state changes
    LaunchedEffect(paymentState) {
        when (paymentState) {
            is PaymentState.Success -> {
                successMessage = "Payment processed successfully!"
                errorMessage = null
            }
            is PaymentState.Error -> {
                errorMessage = (paymentState as PaymentState.Error).message
                successMessage = null
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (isDarkTheme) Color(0xFF121212) else backgroundColor,
                shape = RectangleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .shadow(
                    elevation = cardElevation,
                    spotColor = Color.Black.copy(alpha = if (isDarkTheme) 0.3f else 0.1f),
                    shape = RoundedCornerShape(32.dp)
                ),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(PrimaryColor.copy(alpha = if (isDarkTheme) 0.2f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_bolt_foreground),
                        contentDescription = stringResource(R.string.logo_description),
                        modifier = Modifier.size(70.dp),
                        colorFilter = ColorFilter.tint(if (isDarkTheme) TextColorLight else PrimaryColor)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(durationMillis = 500))
                ) {
                    Text(
                        text = "Payment",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = textColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = "Enter your payment details below to complete your purchase",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = textSecondaryColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Card Number Field
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { if (it.length <= 16) cardNumber = it },
                    label = {
                        Text(
                            "Card Number",
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                        )
                    },
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputBgColor,
                        unfocusedContainerColor = inputBgColor,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = PrimaryColor,
                        unfocusedLabelColor = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                        cursorColor = PrimaryColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Cardholder Name Field
                OutlinedTextField(
                    value = cardholderName,
                    onValueChange = { cardholderName = it },
                    label = {
                        Text(
                            "Cardholder Name",
                            fontWeight = FontWeight.Medium,
                            color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                        )
                    },
                    textStyle = TextStyle(
                        fontWeight = FontWeight.Medium,
                        color = textColor
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = inputBgColor,
                        unfocusedContainerColor = inputBgColor,
                        focusedBorderColor = PrimaryColor,
                        unfocusedBorderColor = borderColor,
                        focusedLabelColor = PrimaryColor,
                        unfocusedLabelColor = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                        cursorColor = PrimaryColor,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Expiry Date and CVV in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Expiry Date Field
                    OutlinedTextField(
                        value = expiryDate,
                        onValueChange = { if (it.length <= 5) expiryDate = it },
                        label = {
                            Text(
                                "Expiry Date",
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                            )
                        },
                        placeholder = { Text("MM/YY") },
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor,
                            focusedBorderColor = PrimaryColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = PrimaryColor,
                            unfocusedLabelColor = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            cursorColor = PrimaryColor,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 8.dp)
                    )

                    // CVV Field
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = { if (it.length <= 3) cvv = it },
                        label = {
                            Text(
                                "CVV",
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color.LightGray else Color.DarkGray
                            )
                        },
                        textStyle = TextStyle(
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBgColor,
                            unfocusedContainerColor = inputBgColor,
                            focusedBorderColor = PrimaryColor,
                            unfocusedBorderColor = borderColor,
                            focusedLabelColor = PrimaryColor,
                            unfocusedLabelColor = if (isDarkTheme) Color.LightGray else Color.DarkGray,
                            cursorColor = PrimaryColor,
                            focusedTextColor = textColor,
                            unfocusedTextColor = textColor
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .weight(0.7f)
                            .padding(bottom = 8.dp)
                    )
                }

                // Error message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300))
                ) {
                    errorMessage?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(
                                    color = Color(0xFFE53935),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Success message
                AnimatedVisibility(
                    visible = successMessage != null,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300))
                ) {
                    successMessage?.let {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(
                                    color = Color(0xFF43A047),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        successMessage = null

                        // Validate fields
                        when {
                            cardNumber.isBlank() -> {
                                errorMessage = "Card number is required"
                                return@Button
                            }
                            cardholderName.isBlank() -> {
                                errorMessage = "Cardholder name is required"
                                return@Button
                            }
                            expiryDate.isBlank() -> {
                                errorMessage = "Expiry date is required"
                                return@Button
                            }
                            cvv.isBlank() -> {
                                errorMessage = "CVV is required"
                                return@Button
                            }
                        }

                        // Process payment
                        paymentViewModel.processPayment(
                            cardNumber = cardNumber,
                            cardholderName = cardholderName,
                            expiryDate = expiryDate,
                            cvv = cvv
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) Color(0xFF333333) else PrimaryColor,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 0.dp
                    )
                ) {
                    Text(
                        "Process Payment",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(
                    onClick = onBackToHome,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.go_back),
                            tint = if (isDarkTheme) TextColorLight else PrimaryColor
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            "Back to Profile",
                            color = if (isDarkTheme) TextColorLight else PrimaryColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    }
                }

                if (paymentState is PaymentState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 16.dp),
                        color = PrimaryColor,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

// Payment State sealed class
sealed class PaymentState {
    object Idle : PaymentState()
    object Loading : PaymentState()
    object Success : PaymentState()
    data class Error(val message: String) : PaymentState()
}

// Payment ViewModel Class
class PaymentViewModel : ViewModel() {
    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    fun processPayment(cardNumber: String, cardholderName: String, expiryDate: String, cvv: String) {
        _paymentState.value = PaymentState.Loading

        // Simulate payment processing (replace with actual implementation)
        viewModelScope.launch {
            delay(1500) // Simulate network delay

            // Simulated validation/processing
            if (cardNumber.length != 16) {
                _paymentState.value = PaymentState.Error("Invalid card number")
                return@launch
            }

            if (!expiryDate.matches(Regex("\\d{2}/\\d{2}"))) {
                _paymentState.value = PaymentState.Error("Invalid expiry date format")
                return@launch
            }

            // For demo purposes, always succeed if validation passes
            _paymentState.value = PaymentState.Success
        }
    }
}