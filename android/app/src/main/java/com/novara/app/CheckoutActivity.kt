package com.novara.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CheckoutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val planName = intent.getStringExtra("plan_name") ?: "Novara 3.2"
        val price = intent.getStringExtra("price") ?: "₹999"

        setContent {
            NovaraCheckoutScreen(
                planName = planName,
                price = price,
                onBack = { finish() }
            )
        }
    }
}

@Composable
fun NovaraCheckoutScreen(
    planName: String,
    price: String,
    onBack: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf("UPI") }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF7F8FC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {

                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "‹",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(end = 12.dp)
                    )

                    Text(
                        text = "Checkout",
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Header
                Text(
                    text = "Complete your purchase",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose a secure payment method",
                    color = Color.Gray,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Payment methods
                PaymentMethodCard(
                    icon = "💳",
                    title = "Credit Card",
                    subtitle = "Visa, Mastercard and more",
                    selected = selectedMethod == "Credit Card",
                    onClick = { selectedMethod = "Credit Card" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PaymentMethodCard(
                    icon = "💳",
                    title = "Debit Card",
                    subtitle = "Pay securely with your debit card",
                    selected = selectedMethod == "Debit Card",
                    onClick = { selectedMethod = "Debit Card" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PaymentMethodCard(
                    icon = "P",
                    title = "PayPal",
                    subtitle = "Pay with your PayPal account",
                    selected = selectedMethod == "PayPal",
                    onClick = { selectedMethod = "PayPal" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PaymentMethodCard(
                    icon = "🇮🇳",
                    title = "UPI",
                    subtitle = "PhonePe, Paytm, BHIM and other UPI apps",
                    selected = selectedMethod == "UPI",
                    onClick = { selectedMethod = "UPI" }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PaymentMethodCard(
                    icon = "G",
                    title = "Google Pay",
                    subtitle = "Fast payment with Google Pay",
                    selected = selectedMethod == "Google Pay",
                    onClick = { selectedMethod = "Google Pay" }
                )

                Spacer(modifier = Modifier.height(25.dp))

                // Order summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            text = "Order Summary",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = planName,
                                fontSize = 15.sp
                            )

                            Text(
                                text = price,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = price,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Pay button
                Button(
                    onClick = {
                        // Real payment gateway will be connected here.
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Pay $price",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "Secure checkout",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PaymentMethodCard(
    icon: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        Color.Black
    } else {
        Color(0xFFE1E3E8)
    }

    val backgroundColor = if (selected) {
        Color(0xFFF1F2F5)
    } else {
        Color.White
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        color = Color(0xFFF3F4F7),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}
