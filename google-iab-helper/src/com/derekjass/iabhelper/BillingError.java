package com.derekjass.iabhelper;

/**
 * Enumeration of all the potential errors that may occur while using the
 */
public enum BillingError {
	/**
	 * Error when a user pressed back or canceled a dialog.
	 */
	USER_CANCELED,
	/**
	 * Error when the billing API version is not supported by the currently
	 * installed version of the Google Play services for the product type.
	 */
	BILLING_UNAVAILABLE,
	/**
	 * Error when the requested product is not available for purchase.
	 */
	ITEM_UNAVAILABLE,
	/**
	 * Error that indicates that either the application was not properly signed
	 * or configured properly in the Play developer console, or if the required
	 * permission is not declared in the manifest.
	 */
	DEVELOPER_ERROR,
	/**
	 * A fatal error occurred during the call to the billing service.
	 */
	ERROR,
	/**
	 * Error when trying to purchase an item that is already owned.
	 */
	ITEM_ALREADY_OWNED,
	/**
	 * Error when trying to consume an item that is not owned.
	 */
	ITEM_NOT_OWNED,
	/**
	 * Error when the connection to the Google Play billing service was
	 * unexpectedly interrupted.
	 */
	REMOTE_EXCEPTION,
	/**
	 * Error associated with the intent that was given by the billing service to
	 * purchase the product.
	 */
	SEND_INTENT_EXCEPTION,
	/**
	 * Error when device does not have the required service to implement in-app
	 * billing.
	 */
	PLAY_SERVICES_UNAVAILABLE,
	/**
	 * Error when the signature fails a validation check.
	 */
	INVALID_SIGNATURE;

	static BillingError fromResponseCode(int code) {
		switch (code) {
		case 1:
			return USER_CANCELED;
		case 3:
			return BILLING_UNAVAILABLE;
		case 4:
			return ITEM_UNAVAILABLE;
		case 5:
			return DEVELOPER_ERROR;
		case 6:
			return ERROR;
		case 7:
			return ITEM_ALREADY_OWNED;
		case 8:
			return ITEM_NOT_OWNED;
		default:
			throw new IllegalArgumentException("Undefined error code: " + code);
		}
	}
}