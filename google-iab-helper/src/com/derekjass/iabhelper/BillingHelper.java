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
import android.util.SparseArray;

import com.android.vending.billing.IInAppBillingService;

/**
 * A helper class to assist with using the Google Play in-app billing service.
 * <h1>Setup</h1>
 * <p>
 */
public class BillingHelper {

	public interface OnErrorListener {
		public void onError(BillingError error);
	}

	public interface OnProductsQueriedListener extends OnErrorListener {
		public void onProductsQueried(List<Product> products);
	}

	public interface OnPurchasesQueriedListener extends OnErrorListener {
		public void onPurchasesQueried(List<Purchase> purchases);
	}

	public interface OnProductPurchasedListener extends OnErrorListener {
		public void onProductPurchased(Purchase purchase);
	}

	public interface OnPurchaseConsumedListener extends OnErrorListener {
		public void onPurchaseConsumed(Purchase purchase);
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

	public enum BillingError {
		USER_CANCELLED,
		BILLING_UNAVAILABLE,
		ITEM_UNAVAILABLE,
		DEVELOPER_ERROR,
		ERROR,
		ITEM_ALREADY_OWNED,
		ITEM_NOT_OWNED,
		REMOTE_EXCEPTION,
		SEND_INTENT_EXCEPTION;

		private static BillingError fromResponseCode(int code) {
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
	private ProductType mProductType;
	private Handler mHandler;
	private ServiceConnection mConnection;
	private IInAppBillingService mService;
	private CountDownLatch mBindLatch;
	private ExecutorService mExecutor;
	private StaticResponse mStaticResponse;
	private SparseArray<OnProductPurchasedListener> mListeners;

	private BillingHelper(Context context, ProductType productType) {
		mConnected = false;
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

	public static BillingHelper newManagedProductHelper(Context context) {
		return new BillingHelper(context, ProductType.MANAGED_PRODUCT);
	}

	public static BillingHelper newSubscriptionHelper(Context context) {
		return new BillingHelper(context, ProductType.SUBSCRIPTION);
	}

	public void connect() {
		mExecutor = Executors.newCachedThreadPool();
		mBindLatch = new CountDownLatch(1);
		mContext.bindService(new Intent(
				"com.android.vending.billing.InAppBillingService.BIND"),
				mConnection, Context.BIND_AUTO_CREATE);
		mListeners = new SparseArray<OnProductPurchasedListener>();
		mConnected = true;
	}

	public void disconnect() {
		mConnected = false;
		mExecutor.shutdownNow();
		mExecutor = null;
		mContext.unbindService(mConnection);
		mService = null;
		mListeners = null;
	}

	public void queryProducts(final List<String> ids,
			final OnProductsQueriedListener listener) {
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
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					ArrayList<String> jsonArray = result
							.getStringArrayList("DETAILS_LIST");
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

	public void queryPurchases(final OnPurchasesQueriedListener listener) {
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
							deliverError(
									BillingError.fromResponseCode(resultCode),
									listener);
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

	public void purchaseProduct(final String productId, final String payload,
			final Activity activity, final int requestCode,
			final OnProductPurchasedListener listener) {
		if (productId == null || activity == null) {
			throw new IllegalArgumentException(
					"productId and activity may not be null");
		}
		checkConnected();
		mExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					mListeners.put(requestCode, listener);
					String sku = getProductId(productId);

					mBindLatch.await();
					Bundle result = mService.getBuyIntent(3,
							mContext.getPackageName(), sku,
							mProductType.token(), payload);

					int resultCode = result.getInt(RESPONSE_CODE);
					if (resultCode != 0) {
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					PendingIntent intent = result.getParcelable("BUY_INTENT");

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

	public void handleActivityResult(int requestCode, int resultCode,
			Intent data) {
		if (resultCode != Activity.RESULT_OK) return;
		OnProductPurchasedListener listener = mListeners.get(requestCode);
		if (listener == null) return;
		int responseCode = data.getIntExtra(RESPONSE_CODE, 6);
		if (responseCode != 0) {
			deliverError(BillingError.fromResponseCode(responseCode), listener);
			return;
		}
		String purchase = data.getStringExtra("INAPP_PURCHASE_DATA");
		String signature = data.getStringExtra("INAPP_DATA_SIGNATURE");
		Purchase result = new Purchase(purchase, signature);
		deliverProductPurchased(result, listener);
	}

	public void consumePurchase(final Purchase purchase,
			final OnPurchaseConsumedListener listener) {
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
						deliverError(BillingError.fromResponseCode(resultCode),
								listener);
						return;
					}

					deliverPurchaseConsumed(purchase, listener);
				} catch (RemoteException e) {
					deliverError(BillingError.REMOTE_EXCEPTION, listener);
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
