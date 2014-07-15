package com.derekjass.iabhelper;

import org.json.JSONException;
import org.json.JSONObject;

public class Product {

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

	Product(String jsonString) {
		try {
			JSONObject jo = new JSONObject(jsonString);
			mProductId = jo.optString(PRODUCT_ID);
			mType = jo.optString(TYPE);
			mPrice = jo.optString(PRICE);
			mTitle = jo.optString(TITLE);
			mDescription = jo.optString(DESCRIPTION);
		} catch (JSONException e) {
			throw new RuntimeException(e);
		}
	}

	public String getmProductId() {
		return mProductId;
	}

	public String getmType() {
		return mType;
	}

	public String getmPrice() {
		return mPrice;
	}

	public String getmTitle() {
		return mTitle;
	}

	public String getmDescription() {
		return mDescription;
	}
}
