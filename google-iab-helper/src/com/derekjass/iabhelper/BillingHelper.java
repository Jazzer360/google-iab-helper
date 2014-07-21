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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;

/**
 * A helper class to assist with using the Google Play in-app billing service.
 * <h1>Setup</h1>
 * <p>
 * The helper uses a callback interface to deliver the results of the calls to
 * Google's in app billing service. So first, you need an implementation of the
 * {@link Callbacks} interface to handle any results of any of the in-app
 * billing queries. Errors are handled by a separate interface (
 * {@link ErrorHandler} ).
 * <p>
 * Next, you must obtain a new {@code BillingHelper} instance from one of the
 * two static factory methods. Which one you use depends on if you're handling
 * managed products, or subscriptions.
 * <ul>
 * <li>{@link #newManagedProductHelper(Context, Callbacks, ErrorHandler)}
 * <li>{@link #newSubscriptionHelper(Context, Callbacks, ErrorHandler)}
 * </ul>
 * 
 * <p>
 * Once you have an instance, you must call {@link #connect()} to set up the
 * connection to the in-app billing service. To avoid resource leaks, when not
 * using the class, {@link #disconnect()} should be called. This should
 * typically be done within the onStart/onStop lifecycle methods of an activity.
 * <p>
 * In order for the callback interface to receive the call to
 * {@link Callbacks#onProductPurchased(Purchase)}, the activity used to call
 * {@link #purchaseProduct(String, Activity, int)} must override it's
 * onActivityResult(int, int, Intent) method to include a call to
 * {@link #handleActivityResult(int, int, Intent)}.
 * <h1>Querying Products</h1>
 * <p>
 * Querying the details of the available in-app products goes like this:
 * 
 * <pre>
 * List&lt;String&gt; skus = new ArrayList&lt;String&gt;();
 * skus.add(&quot;product_1&quot;);
 * skus.add(&quot;product_2&quot;);
 * mBillingHelper.queryProducts(skus);
 * </pre>
 * 
 * Once the Google play service responds, the results are handed back to the
 * callback interface that was provided when getting an instance of the
 * {@code BillingHelper}. The call is made on the main thread of the app.
 * <h1>Querying Purchases</h1>
 * <p>
 * Querying all of the user's purchases of in-app products is simple:
 * 
 * <pre>
 * mBillingHelper.queryPurchases();
 * </pre>
 * 
 * The results are delivered asynchronously to the callback interface.
 * <h1>Purchasing</h1>
 * 
 */
public class BillingHelper {

	/**
	 * Callback to deliver results of the calls made to the billing service.
	 * <p>
	 * These calls are run on the main UI thread.
	 */
	public interface Callbacks {
		public void onProductsQueried(List<Product> products);

		public void onPurchasesQueried(List<Purchase> purchases);

		public void onProductPurchased(Purchase purchase);

		public void onPurchaseConsumed(Purchase purchase);
	}

	public interface ErrorHandler {
		public void onError(Error error);
	}

	private enum ProductType {
		MANAGED_PRODUCT("inapp"), SUBSCRIPTION("subs");

		private String mToken;

		private ProductType(String token) {
			mToken = token;
		}

		private String token() {
			return mToken;
		}
	}

	public enum StaticResponse {
		PURCHASED("android.test.purchased"),
		CANCELED("android.test.canceled"),
		REFUNDED("android.test.refunded"),
		UNAVAILABLE("android.test.unavailable");

		private String mId;

		private StaticResponse(String id) {
			mId = id;
		}

		private String id() {
			return mId;
		}
	}

	public enum Error {
		USER_CANCELLED,
		BILLING_UNAVAILABLE,
		ITEM_UNAVAILABLE,
		DEVELOPER_ERROR,
		ERROR,
		ITEM_ALREADY_OWNED,
		ITEM_NOT_OWNED,
		REMOTE_EXCEPTION,
		SEND_INTENT_EXCEPTION;

		private static Error fromResponseCode(int code) {
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
				throw new IllegalArgumentException("Undefined error code: "
						+ code);
			}
		}
	}

	private static final String RESPONSE_CODE = "RESPONSE_CODE";

	private boolean mConnected;
	private Context mContext;
	private Callbacks mCallbacks;
	private ErrorHandler mErrorHandler;
	private ProductType mProductType;
	private Handler mHandler;
	private ServiceConnection mConnection;
	private IInAppBillingService mService;
	private CountDownLatch mBindLatch;
	private ExecutorService mExecutor;
	private StaticResponse mStaticResponse;

	private BillingHelper(Context context, Callbacks callbacks,
			ErrorHandler errorHandler, ProductType productType) {
		mConnected = false;
		mContext = context.getApplicationContext();
		mCallbacks = callbacks;
		mErrorHandler = errorHandler;
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

	public static BillingHelper newManagedProductHelper(Context context,
			Callbacks callbacks, ErrorHandler errorHandler) {
		return new BillingHelper(context, callbacks, errorHandler,
				ProductType.MANAGED_PRODUCT);
	}

	public static BillingHelper newSubscriptionHelper(Context context,
			Callbacks callbacks, ErrorHandler errorHandler) {
		return new BillingHelper(context, callbacks, errorHandler,
				ProductType.SUBSCRIPTION);
	}

	public void connect() {
		mExecutor = Executors.newCachedThreadPool();
		mBindLatch = new CountDownLatch(1);
		mContext.bindService(new Intent(
				"com.android.vending.billing.InAppBillingService.BIND"),
				mConnection, Context.BIND_AUTO_CREATE);
		mConnected = true;
	}

	public void disconnect() {
		mConnected = false;
		mExecutor.shutdownNow();
		mExecutor = null;
		mContext.unbindService(mConnection);
		mService = null;
	}

	public void queryProducts(final List<String> ids) {
		if (ids == null) {
			throw new IllegalArgumentException("ids may not be null");
		}
		if (ids.size() == 0) {
			throw new IllegalArgumentException("ids must not be empty");
		}
		if (ids.size() > 20) {
			throw new IllegalArgumentException("ids may exceed 20 ids");
		}
		checkConnected();
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
					skuBundle.putStringArrayList("ITEM_ID_LIST", skus);

					mBindLatch.await();
					Bundle result = mService.getSkuDetails(3,
							mContext.getPackageName(), mProductType.token(),
							skuBundle);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(Error.fromResponseCode(resultCode));
						return;
					}

					ArrayList<String> jsonArray = result
							.getStringArrayList("DETAILS_LIST");
					List<Product> products = new ArrayList<Product>();

					for (String json : jsonArray) {
						products.add(new Product(json));
					}

					deliverProductsQueried(products);
				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
		});
	}

	public void queryPurchases() {
		checkConnected();
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String continuationToken = null;
					List<Purchase> purchases = new ArrayList<Purchase>();

					mBindLatch.await();
					do {
						Bundle result = mService.getPurchases(3,
								mContext.getPackageName(),
								mProductType.token(), continuationToken);

						int resultCode = result.getInt(RESPONSE_CODE);
						if (resultCode != 0) {
							deliverError(Error.fromResponseCode(resultCode));
							return;
						}

						ArrayList<String> jsonArray = result
								.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
						ArrayList<String> signatures = result
								.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
						continuationToken = result
								.getString("INAPP_CONTINUATION_TOKEN");

						for (int i = 0; i < jsonArray.size(); i++) {
							String json = jsonArray.get(i);
							String signature = signatures.get(i);
							Purchase purchase = new Purchase(json, signature);
							purchases.add(purchase);
						}
					} while (continuationToken != null);

					deliverPurchasesQueried(purchases);
				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		});
	}

	public void purchaseProduct(final String productId, final String payload,
			final Activity activity, final int requestCode) {
		if (productId == null || activity == null) {
			throw new IllegalArgumentException(
					"productId and activity may not be null");
		}
		checkConnected();
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					String sku = getProductId(productId);

					mBindLatch.await();
					Bundle result = mService.getBuyIntent(3,
							mContext.getPackageName(), sku,
							mProductType.token(), payload);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(Error.fromResponseCode(resultCode));
						return;
					}

					PendingIntent intent = result.getParcelable("BUY_INTENT");

					activity.startIntentSenderForResult(
							intent.getIntentSender(), requestCode,
							new Intent(), 0, 0, 0);

				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (SendIntentException e) {
					deliverError(Error.SEND_INTENT_EXCEPTION);
				}
			}
		});

	}

	public void handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (resultCode != Activity.RESULT_OK) return;
		int responseCode = data.getIntExtra(RESPONSE_CODE, 6);
		if (responseCode != 0) {
			deliverError(Error.fromResponseCode(responseCode));
			return;
		}
		String purchase = data.getStringExtra("INAPP_PURCHASE_DATA");
		String signature = data.getStringExtra("INAPP_DATA_SIGNATURE");
		Purchase result = new Purchase(purchase, signature);
		deliverProductPurchased(result);
	}

	public void consumePurchase(final Purchase purchase) {
		if (mProductType == ProductType.SUBSCRIPTION) {
			throw new UnsupportedOperationException(
					"Cannot consume a subscription");
		}
		checkConnected();
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					int resultCode = mService.consumePurchase(3,
							mContext.getPackageName(),
							purchase.getPurchaseToken());

					if (resultCode != 0) {
						deliverError(Error.fromResponseCode(resultCode));
						return;
					}

					deliverPurchaseConsumed(purchase);
				} catch (RemoteException e) {
					deliverError(Error.REMOTE_EXCEPTION);
				}
			}
		});
	}

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

	private void deliverError(final Error error) {
		if (mErrorHandler == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mErrorHandler.onError(error);
			}
		});
	}

	private void deliverProductsQueried(final List<Product> products) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onProductsQueried(products);
			}
		});
	}

	private void deliverPurchasesQueried(final List<Purchase> purchases) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onPurchasesQueried(purchases);
			}
		});
	}

	private void deliverProductPurchased(final Purchase purchase) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onProductPurchased(purchase);
			}
		});
	}

	private void deliverPurchaseConsumed(final Purchase purchase) {
		if (mCallbacks == null) return;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				mCallbacks.onPurchaseConsumed(purchase);
			}
		});
	}
}
