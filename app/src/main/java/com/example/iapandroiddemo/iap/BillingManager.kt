package com.example.iapandroiddemo.iap

import android.app.Activity
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams
import com.example.iapandroiddemo.MainActivity


class BillingManager(activity: MainActivity) : PurchasesUpdatedListener,
    BillingClientStateListener {
    private var mBillingClient: BillingClient? = null
    private var billingUpdatesListener: IBillingUpdatesListener?
    private var mActivity: Activity?
    private var acknowledgePurchaseResponseListener: AcknowledgePurchaseResponseListener?

    init {
        mActivity = activity
        acknowledgePurchaseResponseListener = activity
        billingUpdatesListener = activity
    }

    /**
     * Closes the connection to the billing service.
     */
    fun destroy() {
        if (mBillingClient != null) {
            mBillingClient!!.endConnection()
            mBillingClient = null
            mActivity = null
            billingUpdatesListener = null
            acknowledgePurchaseResponseListener = null
        }
    }

    /**
     * This is called when the connection to the billing service is lost.
     * You can implement logic to retry connecting to the service in this function.
     * For our purpose, we are only adding a debug log.
     */
    override fun onBillingServiceDisconnected() {
        Log.d(TAG, "Connection to the billing service is lost.")
    }

    /**
     * This is called when the billing setup is complete.
     * When the setup succeeds, a query is made to the billing service to retrieve the purchases.
     * When the setup fails, we'll let MainActivity, which implements IBillingUpdatesListener,
     * decides how to handle the failure.
     */
    override fun onBillingSetupFinished(@NonNull billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Billing setup successful.")

            // Get purchase status.
            queryPurchases()
        } else {
            Log.e(TAG, "Billing setup failed with response code " + billingResult.responseCode)
            billingUpdatesListener!!.onFailedBillingSetup()
        }
    }

    /**
     * Performs actions after receiving notifications about purchase updates.
     */
    override fun onPurchasesUpdated(
        @NonNull billingResult: BillingResult,
        @Nullable purchases: MutableList<Purchase>?,
    ) {
        processPurchase(billingResult, purchases)
    }

    /**
     * Handles purchase status updates.
     */
    fun processPurchase(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            if (purchases != null) {
                for (purchase in purchases) {
                    val purchaseState = purchase.purchaseState
                    if (purchaseState == Purchase.PurchaseState.PURCHASED) {
                        verifyPurchase(purchase)
                    } else if (purchaseState == Purchase.PurchaseState.PENDING) {
                        Log.d(TAG, "Purchase pending...")

                        // Nothing to do here.
                        // When purchase transitions from pending to purchased while the app
                        // is still open, onPurchasesUpdated will be invoked again.
                        // If the transition happened when the app was closed,
                        // the status update will be picked up when the app
                        // connects to the billing service again.
                    }
                }
            }
        } else if (billingResult.responseCode ==
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
        ) {
            billingUpdatesListener!!.onPurchaseUpdated(
                IBillingUpdatesListener.BillingManagerResponse.USER_PURCHASED_OWNED_ITEM
            )
        } else if (billingResult.responseCode ==
            BillingClient.BillingResponseCode.USER_CANCELED
        ) {
            // Don't do anything if user cancels.
            Log.d(TAG, "user canceled")
        } else if (billingResult.responseCode ==
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE
        ) {
            // When Google billing is not available or there is no internet connection,
            // Google billing API will display a message in its own UI. So, in this case,
            // we are only logging a message.
            Log.d(TAG, "billing service unavailable")
        } else if (billingResult.responseCode ==
            BillingClient.BillingResponseCode.DEVELOPER_ERROR
        ) {
            Log.d(
                TAG,
                "Check that the product ID on Google Play Console matches the ID used in the code " +
                        "and ensure the APK is signed with release keys."
            )
        } else {
            billingUpdatesListener!!.onPurchaseUpdated(
                IBillingUpdatesListener.BillingManagerResponse.UNKNOWN_ERROR
            )
        }
    }

    /**
     * Validates a purchase and acknowledges it if it's a new purchase.
     */
    fun verifyPurchase(purchase: Purchase) {
        if (!isPurchaseValid(purchase)) {
            billingUpdatesListener!!.onPurchaseUpdated(
                IBillingUpdatesListener.BillingManagerResponse.FAILED_TO_VALIDATE_PURCHASE
            )
        } else if (!purchase.isAcknowledged) {
            // Acknowledging the purchase. Otherwise, the purchase is automatically
            // refunded to the user after 3 days from the purchase date.
            val acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            mBillingClient!!.acknowledgePurchase(
                acknowledgePurchaseParams,
                acknowledgePurchaseResponseListener!!
            )
        } else {
            // Purchase valid and already acknowledged.
            billingUpdatesListener!!.onPurchaseUpdated(
                IBillingUpdatesListener.BillingManagerResponse.USER_HAS_PURCHASED_ITEM
            )
        }
    }

    /**
     * Initiates the purchase through the billing service.
     */
    fun initiatePurchaseFlow() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .setProductId(IAP_PROD_ID).build()

        val productList = ArrayList<QueryProductDetailsParams.Product?>()
        productList.add(product)

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        mBillingClient!!.queryProductDetailsAsync(params) { billingResult: BillingResult, productDetailsList: QueryProductDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val productDetailsParams =
                    ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetailsList.productDetailsList[0])
                        .build()

                startPurchase(productDetailsParams)
            } else {
                Log.e(
                    TAG, "Filed to obtain product details on purchase initiation. " +
                            "Billing response code: " + billingResult.responseCode
                )
                billingUpdatesListener!!.onPurchaseUpdated(
                    IBillingUpdatesListener.BillingManagerResponse.FAILED_TO_INITIATE_PURCHASE
                )
            }
        }
    }

    /**
     * Connects to the billing service then waits for it to pull the purchases or query the purchases
     * directly if a connection to the billing service was already established.
     *
     *
     * Note: When billing service connects, we will get notified through onBillingSetupFinished.
     * At that point queryPurchases will be called to pull the purchases.
     */
    fun restorePurchases() {
        if (mBillingClient == null || !mBillingClient!!.isReady) {
            // Connect and query for purchases.
            connectToGooglePlay()
        } else {
            // Already connected. Just need to query the purchases.
            queryPurchases()
        }
    }

    /**
     * Validates a purchase.
     *
     *
     * Note: the implementation is not complete. You will need to implement this function once
     * you setup a server with a backend that performs the purchase validation.
     *
     * @param purchase Instance of Purchase to be validated.
     */
    private fun isPurchaseValid(purchase: Purchase): Boolean {
        // Do the following steps to implement this function:
        // 1. Get the purchase token.
        purchase.purchaseToken

        // 2. Send the token to your server to verify purchase's status and validity.

        // 3. return true if the purchase is valid or false otherwise.

        // For the purpose of this example, this function will always return true.
        return true
    }

    /**
     * Connects to Google Play's billing service.
     */
    private fun connectToGooglePlay() {
        if (mBillingClient == null) {
            val pendingPurchasesParams =
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts().build()
            mBillingClient = BillingClient.newBuilder(mActivity!!)
                .setListener(this)
                .enablePendingPurchases(pendingPurchasesParams)
                .build()
        }

        // Check if billing client is not already connected or is not connecting.
        if (!mBillingClient!!.isReady &&
            mBillingClient!!.connectionState != BillingClient.ConnectionState.CONNECTING
        ) {
            mBillingClient!!.startConnection(this)
        } else {
            Log.d(
                TAG, "Billing client is connecting or is already connected. Connection state: "
                        + mBillingClient!!.connectionState
            )
        }
    }

    /**
     * Pulls purchases from the billing service.
     * For the purpose of this example, we are only using 1 non-consumable in-app product.
     */
    private fun queryPurchases() {
        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        mBillingClient!!.queryPurchasesAsync(params) { result: BillingResult?, list: MutableList<Purchase>? ->
            if (result!!.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchase(result, list)
            } else {
                Log.d(
                    TAG,
                    String.format(
                        "query purchases status: %s.",
                        result.responseCode
                    )
                )
            }
        }
    }

    /**
     * Initiates a purchase.
     *
     * @param params instance that contains the details of the product being purchased.
     */
    private fun startPurchase(params: ProductDetailsParams?) {
        val paramsList: MutableList<ProductDetailsParams?> = ArrayList<ProductDetailsParams?>()
        paramsList.add(params)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(paramsList)
            .build()
        mBillingClient!!.launchBillingFlow(mActivity!!, flowParams)
    }

    companion object {
        // Note: the ID should match your in-app product ID on Google Play console.
        private const val IAP_PROD_ID = "com.example.iapandroiddemo.nonconsumable"
        private const val TAG = "Billing Manager"
    }
}
