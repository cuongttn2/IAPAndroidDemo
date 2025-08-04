package com.example.iapandroiddemo.iap;

/**
 * Interface that activities must implement to receive notifications from BillingManager.
 */
public interface IBillingUpdatesListener {
    enum BillingManagerResponse {
        USER_HAS_PURCHASED_ITEM,
        USER_PURCHASED_OWNED_ITEM,
        UNKNOWN_ERROR,
        FAILED_TO_INITIATE_PURCHASE,
        FAILED_TO_VALIDATE_PURCHASE
    }
    void onPurchaseUpdated(BillingManagerResponse billingResponse);
    void onFailedBillingSetup();
}