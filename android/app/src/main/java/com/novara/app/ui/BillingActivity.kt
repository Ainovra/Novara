package com.novara.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.*
import com.novara.app.network.ApiClient
import com.novara.app.ui.theme.NovaraTheme
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

data class BillingTier(
    val id: String,
    val productId: String?,
    val name: String,
    val priceLabel: String,
    val features: List<String>
)

private val TIERS = listOf(
    BillingTier(
        "free", null, "Novara Free", "Free",
        listOf(
            "Novara Fast",
            "Web search",
            "Code",
            "PDF/file analysis",
            "Photo/image analysis",
            "Voice assistant",
            "1 image/month",
            "1 silent video/month",
            "Rewarded ads"
        )
    ),
    BillingTier(
        "plus", "novara_plus", "Novara Plus", "$9.99/month",
        listOf(
            "Novara Fast",
            "Novara Thinking",
            "Novara Omega",
            "40 images/month",
            "5 silent videos/month",
            "5 audio + video/month",
            "No ads"
        )
    ),
    BillingTier(
        "pro", "novara_pro", "Novara V2 Pro", "$19.99/month",
        listOf(
            "Novara Fast",
            "Novara Thinking",
            "Novara Omega • Advanced",
            "80 images/month",
            "10 silent videos/month",
            "10 audio + video/month",
            "No ads"
        )
    ),
    BillingTier(
        "v3.2", "novara_v32", "Novara V3.2", "$34.99/month",
        listOf(
            "Novara Fast",
            "Novara Thinking",
            "Novara Omega • Advanced",
            "120 images/month",
            "15 silent videos/month",
            "15 audio + video/month",
            "Priority support",
            "Early access",
            "No ads"
        )
    )
)

class BillingActivity :
    ComponentActivity(),
    PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        billingClient = BillingClient.newBuilder(this)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .build()

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(
                    result: BillingResult
                ) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        restoreExistingPurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {}
            }
        )

        setContent {
            NovaraTheme {
                BillingScreen(
                    onBack = { finish() },
                    onBuy = { launchPurchase(it) }
                )
            }
        }
    }

    private fun restoreExistingPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryPurchasesAsync
            }

            purchases
                .filter {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                .forEach { purchase ->
                    verifyPurchase(purchase)
                }
        }
    }

    private fun launchPurchase(productId: String) {
        if (!billingClient.isReady) {
            Toast.makeText(
                this,
                "Google Play Billing is not ready.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val query =
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(
                                BillingClient.ProductType.SUBS
                            )
                            .build()
                    )
                )
                .build()

        billingClient.queryProductDetailsAsync(
            query
        ) { billingResult: BillingResult,
            queryProductDetailsResult: QueryProductDetailsResult ->

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {
                Toast.makeText(
                    this,
                    "Unable to load Google Play checkout.",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            val product: ProductDetails =
                queryProductDetailsResult.productDetailsList.firstOrNull()
                    ?: run {
                        Toast.makeText(
                            this,
                            "This subscription is unavailable.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@queryProductDetailsAsync
                    }
            val offer = product
                ?.subscriptionOfferDetails
                ?.firstOrNull()

            if (offer == null) {
                Toast.makeText(
                    this,
                    "This subscription is unavailable.",
                    Toast.LENGTH_LONG
                ).show()
                return@queryProductDetailsAsync
            }

            val params =
                BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams
                                .ProductDetailsParams
                                .newBuilder()
                                .setProductDetails(product)
                                .setOfferToken(offer.offerToken)
                                .build()
                        )
                    )
                    .build()

            billingClient.launchBillingFlow(
                this,
                params
            )
        }
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (
            result.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {
            if (
                result.responseCode !=
                BillingClient.BillingResponseCode.USER_CANCELED
            ) {
                Toast.makeText(
                    this,
                    "Google Play checkout failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        purchases.orEmpty().forEach {
            verifyPurchase(it)
        }
    }

    private fun verifyPurchase(purchase: Purchase) {
        val productId = purchase.products.firstOrNull()
            ?: return

        if (
            productId !in setOf(
                "novara_plus",
                "novara_pro",
                "novara_v32"
            )
        ) {
            return
        }

        MainScope().launch {
            when (
                val result =
                    ApiClient.verifyGooglePurchase(
                        purchase.purchaseToken,
                        productId
                    )
            ) {
                is ApiClient.ApiResult.Success -> {
                    val data = result.data

                    if (!data.verified || !data.ok) {
                        Toast.makeText(
                            this@BillingActivity,
                            "Purchase could not be verified.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    if (
                        purchase.purchaseState ==
                        Purchase.PurchaseState.PURCHASED
                    ) {
                        if (!purchase.isAcknowledged) {
                            val params =
                                AcknowledgePurchaseParams
                                    .newBuilder()
                                    .setPurchaseToken(
                                        purchase.purchaseToken
                                    )
                                    .build()

                            billingClient.acknowledgePurchase(
                                params
                            ) {}
                        }

                        Toast.makeText(
                            this@BillingActivity,
                            "Verified! ${data.plan} is now active.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                is ApiClient.ApiResult.Failure -> {
                    Toast.makeText(
                        this@BillingActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        if (::billingClient.isInitialized) {
            billingClient.endConnection()
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillingScreen(
    onBack: () -> Unit,
    onBuy: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usage by remember {
        mutableStateOf<ApiClient.UsageResponse?>(null)
    }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadUsage() {
        scope.launch {
            loading = true
            error = null

            when (val result = ApiClient.getUsage()) {
                is ApiClient.ApiResult.Success ->
                    usage = result.data

                is ApiClient.ApiResult.Failure ->
                    error = result.message
            }

            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadUsage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Novara Plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            Text(
                "Your Novara plan",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            usage?.let {
                Text(
                    "Current plan: ${it.plan}",
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                UsageCard(
                    "Images",
                    it.images.used,
                    it.images.limit
                )

                Spacer(Modifier.height(8.dp))

                UsageCard(
                    "Silent videos",
                    it.silentVideos.used,
                    it.silentVideos.limit
                )

                Spacer(Modifier.height(8.dp))

                UsageCard(
                    "Audio + video",
                    it.audioVideo.used,
                    it.audioVideo.limit
                )

                Spacer(Modifier.height(20.dp))
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(Modifier.height(12.dp))
            }

            TIERS.forEach { tier ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            tier.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(tier.priceLabel)

                        Spacer(Modifier.height(10.dp))

                        tier.features.forEach { feature ->
                            Row(
                                modifier = Modifier.padding(
                                    vertical = 3.dp
                                ),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )

                                Spacer(Modifier.width(8.dp))
                                Text(feature)
                            }
                        }

                        tier.productId?.let { productId ->
                            Spacer(Modifier.height(12.dp))

                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onBuy(productId)
                                }
                            ) {
                                Text("Subscribe")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(
    title: String,
    used: Int,
    limit: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(4.dp))

            Text("$used / $limit used")
        }
    }
}
