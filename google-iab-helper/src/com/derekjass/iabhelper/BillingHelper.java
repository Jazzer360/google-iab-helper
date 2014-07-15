package com.derekjass.iabhelper;

import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;

import com.android.vending.billing.IInAppBillingService;

public class BillingHelper {

	public interface Callbacks {
		public void onProductsQueried(List<Product> products, ProductType type);

		public void onPurchasesQueried(List<Purchase> purchases);

		public void onProductPurchased(Purchase purchase);

		public void onPurchaseConsumed(Purchase purchase);
	}

	public interface ErrorHandler {
		public void onError(Error error);
	}

	public enum ProductType {
		IN_APP("inapp"), SUBSCRIPTION("subs");

		String mToken;

		private ProductType(String token) {
			mToken = token;
		}
	}

	public enum Error {
		USER_CANCELLED, BILLING_UNAVAILABLE, ITEM_UNAVAILABLE, DEVELOPER_ERROR, ERROR, ITEM_ALREADY_OWNED, ITEM_NOT_OWNED, REMOTE_EXCEPTION, PLAY_SERVICES_UNAVAILABLE;

		static Error fromResponseCode(int code) {
			switch (code) {
			case 1:
				return USER_CANCELLED;
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
				throw new IllegalArgumentException("Invalid error code: "
						+ code);
			}
		}
	}

	private Activity mActivity;
	private String mApiKey;
	private Callbacks mCallbacks;
	private Handler mHandler;
	private ServiceConnection mConnection;
	private IInAppBillingService mService;

	public BillingHelper(Activity activity, Callbacks callbacks) {
		this(activity, callbacks, null, new Handler());
	}

	public BillingHelper(Activity activity, Callbacks callbacks, String apiKey) {
		this(activity, callbacks, apiKey, new Handler());
	}

	public BillingHelper(Activity activity, Callbacks callbacks, Handler handler) {
		this(activity, callbacks, null, handler);
	}

	public BillingHelper(Activity activity, Callbacks callbacks, String apiKey,
			Handler handler) {
		mActivity = activity;
		mApiKey = apiKey;
		mCallbacks = callbacks;
		mHandler = handler;
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {
			}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
			}
		};
	}

	public void connect() {
		mActivity.bindService(new Intent(
				"com.android.vending.billing.InAppBillingService.BIND"),
				mConnection, 0);
	}

	public void queryProducts(ProductType type, ErrorHandler errorHandler) {

	}

	public void queryPurchases(ProductType type) {

	}

	public void purchaseProduct(Product product) {

	}

	public void purchaseProduct(String productId, ProductType type) {

	}

	public void consumePurchase(Purchase purchase) {

	}

	public void disconnect() {
		mActivity.unbindService(mConnection);
		mService = null;
	}
}
