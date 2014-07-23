package com.derekjass.iabhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.SparseArray;

import com.android.vending.billing.IInAppBillingService;

/**
 * A helper class to assist with using the Google Play in-app billing service.
 * This handles all of the operations asynchronously where possible. All
 * callbacks given to the various methods will all be run in the UI thread of
 * the application.
 * 
 * <h1>Setup</h1>
 * <p>
 * Any application using the Google Play in-app billing will require that the
 * following permission be declared in the manifest file.
 * 
 * <pre>
 * &ltuses-permission android:name="com.android.vending.BILLING" /&gt
 * </pre>
 * <p>
 * To obtain an instance of the {@code BillingHelper} from one of the static
 * factory methods depending on whether you want to handle managed products or
 * subscriptions ({@link #newManagedProductHelper(Context)} or
 * {@link #newSubscriptionHelper(Context)}).
 * <p>
 * Before making any calls, the instance must first connect to the Google Play
 * billing service, which can be done by calling {@link #connect()}. When done
 * with the service, a call should be made to {@link #disconnect()} to release
 * any resources no longer needed. These calls are usually made in the
 * onStart/onStop methods of an Activity.
 * 
 * @author Derek Jass
 */
public class BillingHelper {

	/**
	 * Callback to deliver any errors that occur during the billing process.
	 */
	public interface OnErrorListener {
		/**
		 * Called when an error occured during the requested billing operation.
		 * 
		 * @param error
		 *            error that occured
		 */
		public void onError(BillingError error);
	}

	/**
	 * Callback to deliver the result of a product query.
	 */
	public interface OnProductsQueriedListener extends OnErrorListener {
		/**
		 * Called after a successful product query.
		 * 
		 * @param products
		 *            the list of products queried
		 */
		public void onProductsQueried(List<Product> products);
	}

	/**
	 * Callback to deliver the result of a purchase query.
	 */
	public interface OnPurchasesQueriedListener extends OnErrorListener {
		/**
		 * Called after a successful purchase query.
		 * 
		 * @param purchases
		 *            a list of all purchases
		 */
		public void onPurchasesQueried(List<Purchase> purchases);
	}

	/**
	 * Callback to deliver the result of a purchase.
	 */
	public interface OnProductPurchasedListener extends OnErrorListener {
		/**
		 * Called after a successful purchase.
		 * 
		 * @param purchase
		 *            the result of the purchase
		 */
		public void onProductPurchased(Purchase purchase);
	}

	/**
	 * Callback to deliver the result of a consumed purchase.
	 */
	public interface OnPurchaseConsumedListener extends OnErrorListener {
		/**
		 * Called after a successful purchase consumption request.
		 * 
		 * @param purchase
		 *            the purchase that was consumed
		 */
		public void onPurchaseConsumed(Purchase purchase);
	}

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
		 * Error that indicates that either the application was not properly
		 * signed or configured properly in the Play developer console, or if
		 * the required permission is not declared in the manifest.
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
		 * Error associated with the intent that was given by the billing
		 * service to purchase the product.
		 */
		SEND_INTENT_EXCEPTION,
		/**
		 * Error when device does not have the required service to implement
		 * in-app billing.
		 */
		PLAY_SERVICES_UNAVAILABLE;

		private static BillingError fromResponseCode(int code) {
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
				throw new IllegalArgumentException("Undefined error code: "
						+ code);
			}
		}
	}

	private static final String RESPONSE_CODE = "RESPONSE_CODE";
	private static final String ITEM_ID_LIST = "ITEM_ID_LIST";
	private static final String DETAILS_LIST = "DETAILS_LIST";
	private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";
	private static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
	private static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
	private static final String BUY_INTENT = "BUY_INTENT";
	private static final String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";
	private static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";

	private boolean mConnected;
	private boolean mServiceAvailable;
	private String mProductType;
	private Context mContext;
	private Handler mHandler;
	private ServiceConnection mConnection;
	private IInAppBillingService mService;
	private CountDownLatch mBindLatch;
	private ExecutorService mExecutor;
	private StaticResponse mStaticResponse;
	private SparseArray<OnProductPurchasedListener> mListeners;

	private BillingHelper(Context context, String productType) {
		mConnected = false;
		mServiceAvailable = true;
		mContext = context.getApplicationContext();
		mProductType = productType;
		mHandler = new Handler(Looper.getMainLooper());
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
				mBindLatch.countDown();
			}
		};
	}

	/**
	 * Returns a new {@code BillingHelper} configured to handle requests for
	 * in-app managed products. A context is required in order to successfully
	 * bind to the billing service. It is also used to acquire the package name
	 * which is required in all billing requests.
	 * <p>
	 * The context may not be {@code null}.
	 * 
	 * @param context
	 *            application context
	 * @return a BillingHelper configured to handle managed products
	 */
	public static BillingHelper newManagedProductHelper(Context context) {
		return new BillingHelper(context, "inapp");
	}

	/**
	 * Returns a new {@code BillingHelper} configured to handle requests for
	 * subscriptions. A context is required in order to succesfully bind to the
	 * billing service. It is also used to acquire the package name which is
	 * required in all billing requests.
	 * 
	 * @param context
	 *            application context
	 * @return a BillingHelper configured to handle subscriptions
	 */
	public static BillingHelper newSubscriptionHelper(Context context) {
		return new BillingHelper(context, "subs");
	}

	/**
	 * Initiates the connection to the Google Play billing service. Must be
	 * called prior to making any billing requests. This is typically done in
	 * the onStart() method of an Activity.
	 */
	public synchronized void connect() {
		if (mConnected) return;
		Intent intent = new Intent(
				"com.android.vending.billing.InAppBillingService.BIND");
		intent.setPackage("com.android.vending");
		List<ResolveInfo> services = mContext.getPackageManager()
				.queryIntentServices(intent, 0);
		mServiceAvailable = services != null && !services.isEmpty();
		if (mServiceAvailable) {
			mExecutor = Executors.newCachedThreadPool();
			mBindLatch = new CountDownLatch(1);
			mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
			mListeners = new SparseArray<OnProductPurchasedListener>();
		}
		mConnected = true;
	}

	/**
	 * Disconnects this helper from the Google Play billing service. Should be
	 * called when the service is no longer needed. This is typically done in
	 * the onStop() method of an Activity.
	 */
	public synchronized void disconnect() {
		if (!mConnected) return;
		mConnected = false;
		if (mServiceAvailable) {
			mExecutor.shutdownNow();
			mExecutor = null;
			mContext.unbindService(mConnection);
			mService = null;
			mListeners = null;
		}
	}

	/**
	 * Asynchronously queries the product IDs passed in the ids parameter. The
	 * results of this call will be delivered to the implementation of
	 * {@link OnProductsQueriedListener} in the main thread of the app.
	 * 
	 * @param ids
	 *            list containing at least one, but no more than 20, product ids
	 *            to get additional information about
	 * @param listener
	 *            callback to deliver the results of the query
	 */
	public void queryProducts(final List<String> ids,
			final OnProductsQueriedListener listener) {
		if (ids == null) {
			throw new IllegalArgumentException("ids may not be null");
		}
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("ids must not be empty");
		}
		if (ids.size() > 20) {
			throw new IllegalArgumentException("ids may not exceed 20 ids");
		}
		checkConnected();
		if (!mServiceAvailable) {
			deliverError(BillingError.PLAY_SERVICES_UNAVAILABLE, listener);
			return;
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					ArrayList<String> skus = new ArrayList<String>();
					for (String id : ids) {
						if (!skus.contains(id)) {
							skus.add(id);
						}
					}

					Bundle skuBundle = new Bundle();
					skuBundle.putStringArrayList(ITEM_ID_LIST, skus);

					mBindLatch.await();
					Bundle result = mService.getSkuDetails(3,
							mContext.getPackageName(), mProductType, skuBundle);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					ArrayList<String> jsonArray = result
							.getStringArrayList(DETAILS_LIST);
					List<Product> products = new ArrayList<Product>();

					for (String json : jsonArray) {
						products.add(new Product(json));
					}

					deliverProductsQueried(products, listener);
				} catch (RemoteException e) {
					deliverError(BillingError.REMOTE_EXCEPTION, listener);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	/**
	 * Asynchronously queries all completed purchases for the application. The
	 * results of this call will be delivered to the implementation of the
	 * {@link OnPurchasesQueriedListener} in the main thread of the app.
	 * 
	 * @param listener
	 *            callback to deliver the results of the query
	 */
	public void queryPurchases(final OnPurchasesQueriedListener listener) {
		checkConnected();
		if (!mServiceAvailable) {
			deliverError(BillingError.PLAY_SERVICES_UNAVAILABLE, listener);
			return;
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String continuationToken = null;
					List<Purchase> purchases = new ArrayList<Purchase>();

					mBindLatch.await();
					do {
						Bundle result = mService.getPurchases(3,
								mContext.getPackageName(), mProductType,
								continuationToken);

						int resultCode = result.getInt(RESPONSE_CODE);
						if (resultCode != 0) {
							deliverError(
									BillingError.fromResponseCode(resultCode),
									listener);
							return;
						}

						ArrayList<String> jsonArray = result
								.getStringArrayList(INAPP_PURCHASE_DATA_LIST);
						ArrayList<String> signatures = result
								.getStringArrayList(INAPP_DATA_SIGNATURE_LIST);
						continuationToken = result
								.getString(INAPP_CONTINUATION_TOKEN);

						for (int i = 0; i < jsonArray.size(); i++) {
							String json = jsonArray.get(i);
							String signature = signatures.get(i);
							Purchase purchase = new Purchase(json, signature);
							purchases.add(purchase);
						}
					} while (continuationToken != null);

					deliverPurchasesQueried(purchases, listener);
				} catch (RemoteException e) {
					deliverError(BillingError.REMOTE_EXCEPTION, listener);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
	}

	/**
	 * Starts the purchasing process for the given product ID. When properly
	 * configured, the results of this call will be delivered to the
	 * implementation of the {@link OnProductPurchasedListener} in the main
	 * thread of the app. The {@code payload} is optional and may be used as a
	 * security measure to ensure the response from the billing service is
	 * valid.
	 * <p>
	 * Additional Requirements:
	 * <p>
	 * In order for the associated listener to receive the result of this call,
	 * the Activity passed to this call must override the
	 * {@code onActivityResult(int, int, Intent)} method and within it, include
	 * a call to {@link #handleActivityResult(int, int, Intent)} with the
	 * parameters as they are given in the {@code onActivityResult} method.
	 * <p>
	 * The {@code requestCode} parameter is the value that will be passed back
	 * along with the result of the purchase. This code is used for the
	 * following:
	 * <ul>
	 * <li>It's used to identify the listener you passed to this call. If you
	 * are calling this method with different listeners, each listener must be
	 * associated with a different {@code requestCode}.
	 * <li>If your Activity implements
	 * {@code onActivityResult(int, int, Intent)} for other purposes, this value
	 * will be the {@code requestCode} passed to the {@code onActivityResult}
	 * method. It may then be used to identify which requests were initiated by
	 * this billing service, and therefore, which calls should be forward to
	 * {@link #handleActivityResult(int, int, Intent)}.
	 * </ul>
	 * 
	 * @param productId
	 *            product ID to purchase
	 * @param payload
	 *            (optional) developer payload to be returned with the purchase.
	 *            May be {@code null} if not used
	 * @param activity
	 *            activity that is used to start the purchase that should
	 *            override it's {@code onActivityResult} method to forward to
	 *            this service's {@link #handleActivityResult(int, int, Intent)}
	 *            method
	 * @param requestCode
	 *            code that identifies both this request, and the listener
	 *            passed
	 * @param listener
	 *            callback to deliver the results of the purchase
	 */
	public void purchaseProduct(final String productId, final String payload,
			final Activity activity, final int requestCode,
			final OnProductPurchasedListener listener) {
		if (productId == null || activity == null) {
			throw new IllegalArgumentException(
					"productId and activity may not be null");
		}
		checkConnected();
		if (!mServiceAvailable) {
			deliverError(BillingError.PLAY_SERVICES_UNAVAILABLE, listener);
			return;
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					mListeners.put(requestCode, listener);
					String sku = getProductId(productId);

					mBindLatch.await();
					Bundle result = mService.getBuyIntent(3,
							mContext.getPackageName(), sku, mProductType,
							payload);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					PendingIntent intent = result.getParcelable(BUY_INTENT);

					activity.startIntentSenderForResult(
							intent.getIntentSender(), requestCode,
							new Intent(), 0, 0, 0);

				} catch (RemoteException e) {
					deliverError(BillingError.REMOTE_EXCEPTION, listener);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (SendIntentException e) {
					deliverError(BillingError.SEND_INTENT_EXCEPTION, listener);
				}
			}
		});

	}

	/**
	 * Should only be called by an Activity's
	 * {@code onActivityResult(int, int, Intent)} method to forward the results
	 * to this helper. For notes on using, check the documentation on
	 * {@link #purchaseProduct(String, String, Activity, int, OnProductPurchasedListener)}
	 * 
	 * @param requestCode
	 *            the {@code requestCode} passed to {@code onActivityResult}
	 * @param resultCode
	 *            the {@code resultCode} passed to {@code onActivityResult}
	 * @param data
	 *            the {@code data} Intent passed to {@code onActivityResult}
	 */
	public void handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (resultCode != Activity.RESULT_OK || mListeners == null) return;
		OnProductPurchasedListener listener = mListeners.get(requestCode);
		if (listener == null) return;
		int responseCode = data.getIntExtra(RESPONSE_CODE, 6);
		if (responseCode != 0) {
			deliverError(BillingError.fromResponseCode(responseCode), listener);
			return;
		}
		String purchase = data.getStringExtra(INAPP_PURCHASE_DATA);
		String signature = data.getStringExtra(INAPP_DATA_SIGNATURE);
		Purchase result = new Purchase(purchase, signature);
		deliverProductPurchased(result, listener);
	}

	/**
	 * Asynchronously consumes a purchased product. The result of this action
	 * will be delivered to the implementation of the
	 * {@link OnPurchaseConsumedListener} in the main thread of the app. The
	 * purchase delivered to the listener will be the same one that was passed
	 * to this method initially. If the consumption was not successful, no call
	 * will be made to the
	 * {@link OnPurchaseConsumedListener#onPurchaseConsumed(Purchase)} method.
	 * 
	 * @param purchase
	 *            purchase to consume
	 * @param listener
	 *            callback to deliver the results of the consumption request
	 */
	public void consumePurchase(final Purchase purchase,
			final OnPurchaseConsumedListener listener) {
		if (mProductType.equals("subs")) {
			throw new UnsupportedOperationException(
					"Cannot consume a subscription");
		}
		checkConnected();
		if (!mServiceAvailable) {
			deliverError(BillingError.PLAY_SERVICES_UNAVAILABLE, listener);
			return;
		}
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					mBindLatch.await();
					int resultCode = mService.consumePurchase(3,
							mContext.getPackageName(),
							purchase.getPurchaseToken());

					if (resultCode != 0) {
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					deliverPurchaseConsumed(purchase, listener);
				} catch (RemoteException e) {
					deliverError(BillingError.REMOTE_EXCEPTION, listener);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	/**
	 * Configures this helper to use the StaticResponse specified for all
	 * requests made to
	 * {@link #purchaseProduct(String, String, Activity, int, OnProductPurchasedListener)}
	 * . To remove any prior configuration, simply call this method with
	 * {@code null} as the parameter.
	 * 
	 * @param response
	 *            StaticResponse to replace any purchase calls with
	 */
	public void setStaticResponse(StaticResponse response) {
		mStaticResponse = response;
	}

	private void checkConnected() {
		if (!mConnected) {
			throw new IllegalStateException("Must call connect() before using");
		}
	}

	private String getProductId(String id) {
		return mStaticResponse != null ? mStaticResponse.id() : id;
	}

	private void deliverError(final BillingError error,
			final OnErrorListener listener) {
		if (listener == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				listener.onError(error);
			}
		});
	}

	private void deliverProductsQueried(final List<Product> products,
			final OnProductsQueriedListener listener) {
		if (listener == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				listener.onProductsQueried(products);
			}
		});
	}

	private void deliverPurchasesQueried(final List<Purchase> purchases,
			final OnPurchasesQueriedListener listener) {
		if (listener == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				listener.onPurchasesQueried(purchases);
			}
		});
	}

	private void deliverProductPurchased(final Purchase purchase,
			final OnProductPurchasedListener listener) {
		if (listener == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				listener.onProductPurchased(purchase);
			}
		});
	}

	private void deliverPurchaseConsumed(final Purchase purchase,
			final OnPurchaseConsumedListener listener) {
		if (listener == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				listener.onPurchaseConsumed(purchase);
			}
		});
	}
}
