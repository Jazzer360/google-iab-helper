package com.derekjass.iabhelper;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An immutable class representing an in-app billing purchase.
 */
public class Purchase {

	private static final String ORDER_ID = "orderId";
	private static final String PACKAGE_NAME = "packageName";
	private static final String PRODUCT_ID = "productId";
	private static final String PURCHASE_TIME = "purchaseTime";
	private static final String PURCHASE_STATE = "purchaseState";
	private static final String DEVELOPER_PAYLOAD = "developerPayload";
	private static final String PURCHASE_TOKEN = "purchaseToken";

	public static final int PURCHASE_STATE_PURCHASED = 0;
	public static final int PURCHASE_STATE_CANCELED = 1;
	public static final int PURCHASE_STATE_REFUNDED = 2;

	private final String mOrderId;
	private final String mPackageName;
	private final String mProductId;
	private final long mPurchaseTime;
	private final int mPurchaseState;
	private final String mDeveloperPayload;
	private final String mPurchaseToken;
	private final String mSignature;
	private final String mJson;

	Purchase(String json, String signature) {
		mSignature = signature;
		mJson = json;
		try {
			JSONObject jo = new JSONObject(json);
			mOrderId = jo.optString(ORDER_ID);
			mPackageName = jo.optString(PACKAGE_NAME);
			mProductId = jo.optString(PRODUCT_ID);
			mPurchaseTime = jo.optLong(PURCHASE_TIME);
			mPurchaseState = jo.optInt(PURCHASE_STATE);
			mDeveloperPayload = jo.optString(DEVELOPER_PAYLOAD);
			mPurchaseToken = jo.optString(PURCHASE_TOKEN);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the unique order identifier for the associated purchase
	 * corresponding to the Google Wallet Order ID.
	 * 
	 * @return the unique order identifier for this purchase
	 */
	public String getOrderId() {
		return mOrderId;
	}

	/**
	 * Returns the application package name from which the purchase originated.
	 * 
	 * @return the application package name
	 */
	public String getPackageName() {
		return mPackageName;
	}

	/**
	 * Returns the item's ID for this purchase. This is the ID specified for the
	 * associated product from the Google play developer console.
	 * 
	 * @return the purchased item's ID
	 */
	public String getProductId() {
		return mProductId;
	}

	/**
	 * Returns the time the product was purchased in milliseconds since the
	 * epoch (Jan 1, 1970).
	 * 
	 * @return the time the product was purchased
	 */
	public long getPurchaseTime() {
		return mPurchaseTime;
	}

	/**
	 * Returns one of three possible values that represents the purchased state
	 * of the associated product:
	 * 
	 * <ul>
	 * <li>{@link PURCHASE_STATE_PURCHASED}
	 * <li>{@link PURCHASE_STATE_CANCELED}
	 * <li>{@link PURCHASE_STATE_REFUNDED}
	 * </ul>
	 * 
	 * @return
	 */
	public int getPurchaseState() {
		return mPurchaseState;
	}

	/**
	 * Returns the developer payload that was originally submitted when
	 * purchasing the associated product.
	 * 
	 * @return the developer payload submitted when purchase was initiated
	 */
	public String getDeveloperPayload() {
		return mDeveloperPayload;
	}

	/**
	 * Returns a token that uniquely identifies this purchase.
	 * 
	 * @return a token that uniquely identifies this purchase
	 */
	public String getPurchaseToken() {
		return mPurchaseToken;
	}

	/**
	 * Returns the original JSON data that was signed with the private key of
	 * the developer.
	 * 
	 * @return signature of JSON data signed by the developer's private key
	 */
	public String getSignature() {
		return mSignature;
	}

	/**
	 * Returns the original JSON data that was returned by Google Play's in-app
	 * billing service.
	 * 
	 * @return JSON of this purchase
	 */
	public String getJson() {
		return mJson;
	}

	@Override
	public String toString() {
		return "Purchase: " + mJson;
	}
}
