package com.derekjass.iabhelper;

/**
 * Enumeration of all the pre-defined static test responses that may be used
 * to test an in-app billing app.
 */
public enum StaticResponse {
	/**
	 * Represents the test product id: {@code "android.test.purchased"}
	 * <p>
	 * When used, any request made to purchase a product will return as
	 * though the purchase was successful.
	 * <p>
	 * <i>Note: After successfully purchasing, the item is considered as
	 * 'owned' and may not again be purchased until it is consumed.</i>
	 */
	PURCHASED("android.test.purchased"),
	/**
	 * Represents the test product id: {@code "android.test.canceled"}
	 * <p>
	 * When used, any request made to purchase a product will return as
	 * though the purchase was canceled.
	 */
	CANCELED("android.test.canceled"),
	/**
	 * Represents the test product id: {@code "android.test.refunded"}
	 * <p>
	 * When used, any request made to purchase a product will return as
	 * though the purchase was refunded.
	 */
	REFUNDED("android.test.refunded"),
	/**
	 * Represents the test product id: {@code "android.test.unavailable"}
	 * <p>
	 * When used, any request made to purchase a product will return as
	 * though the product is unavailable for purchase.
	 */
	UNAVAILABLE("android.test.unavailable");

	private String mId;

	private StaticResponse(String id) {
		mId = id;
	}

	/**
	 * Returns the product ID associated with this enumeration.
	 * 
	 * @return product ID of this enumeration
	 */
	public String id() {
		return mId;
	}
}