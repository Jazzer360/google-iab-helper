package com.derekjass.iabhelper;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * An immutable class representing an in-app product listing.
 */
public class Product {

	/**
	 * String that represents the product type for managed products.
	 */
	public static final String MANAGED_PRODUCT = "inapp";
	/**
	 * String that represents the product type for subscriptions.
	 */
	public static final String SUBSCRIPTION = "subs";

	private static final String PRODUCT_ID = "productId";
	private static final String TYPE = "type";
	private static final String PRICE = "price";
	private static final String TITLE = "title";
	private static final String DESCRIPTION = "description";

	private final String mProductId;
	private final String mType;
	private final String mPrice;
	private final String mTitle;
	private final String mDescription;
	private final String mJson;

	Product(String json) {
		mJson = json;
		try {
			JSONObject jo = new JSONObject(json);
			mProductId = jo.optString(PRODUCT_ID);
			mType = jo.optString(TYPE);
			mPrice = jo.optString(PRICE);
			mTitle = jo.optString(TITLE);
			mDescription = jo.optString(DESCRIPTION);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the product ID of the product, also known as the SKU.
	 * 
	 * @return the product ID
	 */
	public String getProductId() {
		return mProductId;
	}

	/**
	 * Returns the product type. This may be one of two values:
	 * 
	 * <ul>
	 * <li>{@link #MANAGED_PRODUCT}
	 * <li>{@link #SUBSCRIPTION}
	 * </ul>
	 * 
	 * @return the product type
	 */
	public String getType() {
		return mType;
	}

	/**
	 * Returns the price of the product as a formatted string including the
	 * currency sign.
	 * 
	 * <p>
	 * Example - {@code "$2.99"}
	 * 
	 * @return the price of the product
	 */
	public String getPrice() {
		return mPrice;
	}

	/**
	 * Returns the title of the product as was entered in the Google play
	 * developer console.
	 * 
	 * @return the title of the product
	 */
	public String getTitle() {
		return mTitle;
	}

	/**
	 * Returns the description of the product as was entered in the Google play
	 * developer console.
	 * 
	 * @return the description of the product
	 */
	public String getDescription() {
		return mDescription;
	}

	/**
	 * Returns {@code true} if the product is an in-app managed product.
	 * 
	 * @return {@code true} if the product is a managed product
	 */
	public boolean isManagedProduct() {
		return mType.equals(MANAGED_PRODUCT);
	}

	/**
	 * Returns {@code true} if the product is a subscription.
	 * 
	 * @return {@code true} if the product is a subscription
	 */
	public boolean isSubscription() {
		return mType.equals(SUBSCRIPTION);
	}

	@Override
	public String toString() {
		return "Product: " + mJson;
	}
}
