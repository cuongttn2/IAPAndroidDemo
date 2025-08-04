package com.example.iapandroiddemo

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.billingclient.api.AcknowledgePurchaseResponseListener
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingResult
import com.example.iapandroiddemo.iap.BillingManager
import com.example.iapandroiddemo.iap.IBillingUpdatesListener
import com.example.iapandroiddemo.iap.IBillingUpdatesListener.BillingManagerResponse
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class MainActivity : AppCompatActivity(), AcknowledgePurchaseResponseListener,
    IBillingUpdatesListener {

    private var mBillingManager: BillingManager? = null
    private lateinit var btnNonConsumable: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mapping()
        setupAction()

    }

    override fun onResume() {
        super.onResume()
        if (mBillingManager == null) {
            mBillingManager = BillingManager(this)
        }
        mBillingManager!!.restorePurchases()
    }

    override fun onDestroy() {
        if (mBillingManager != null) {
            mBillingManager!!.destroy()
            mBillingManager = null
        }
        super.onDestroy()
    }

    private fun mapping() {
        btnNonConsumable = findViewById(R.id.btnNonConsumable)
    }

    private fun setupAction() {
        btnNonConsumable.setOnClickListener { v: View? -> mBillingManager?.initiatePurchaseFlow() }
    }

    override fun onAcknowledgePurchaseResponse(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            // Here you can implement the logic to the unlock the product for the user.
            // ...
            /**/// */

            runOnUiThread(Runnable {
                btnNonConsumable.text = getString(R.string.iap_buy_button_disabled_label)
                btnNonConsumable.isEnabled = false

                val dialogTitle: String? = getString(R.string.iap_purchase_successful_title)
                val dialogMsg: String? = getString(R.string.iap_purchase_successful_msg)
                val successfulPurchaseDialog = createSimpleAlertDialog(dialogTitle, dialogMsg)
                successfulPurchaseDialog.show()
            })
        } else {
            Log.d(
                TAG, java.lang.String.format(
                    "Purchase ack returned an error. response code: %d.",
                    billingResult.responseCode
                )
            )
        }
    }

    override fun onPurchaseUpdated(billingResponse: BillingManagerResponse?) {
        if (billingResponse != null) {
            runOnUiThread(Runnable {
                if (billingResponse == BillingManagerResponse.USER_HAS_PURCHASED_ITEM ||
                    billingResponse == BillingManagerResponse.USER_PURCHASED_OWNED_ITEM
                ) {
                    btnNonConsumable.text = getString(R.string.iap_buy_button_disabled_label)
                    btnNonConsumable.isEnabled = false

                    // Here you may need to add logic to ensure that the purchased product is unlocked
                    // for the user.
                } else {
                    btnNonConsumable.text = getString(R.string.iap_buy_button_enabled_label)
                    btnNonConsumable.isEnabled = true

                    // Here you can handle the errors as you see fit. For the purpose of this demo,
                    // we are only handling purchase initiation and validation errors.
                    if (billingResponse == BillingManagerResponse.FAILED_TO_INITIATE_PURCHASE) {
                        val errorDialog = createSimpleAlertDialog(
                            getString(R.string.iap_purchase_failed_title),
                            getString(R.string.iap_failed_to_initiate_purchase_msg)
                        )
                        errorDialog.show()
                    } else if (billingResponse == BillingManagerResponse.FAILED_TO_VALIDATE_PURCHASE) {
                        val errorDialog = createSimpleAlertDialog(
                            getString(R.string.iap_purchase_failed_title),
                            getString(R.string.iap_purchase_validation_failed_msg)
                        )
                        errorDialog.show()
                    }
                }
            })
        }
    }

    override fun onFailedBillingSetup() {
        Log.d(TAG, "Billing setup failed")
    }

    private fun createSimpleAlertDialog(title: String?, message: String?): AlertDialog {
        return MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "Close",
                { dialog, which -> dialog.cancel() })
            .create()
    }

    companion object {
        private const val TAG: String = "MainActivity"
    }
}