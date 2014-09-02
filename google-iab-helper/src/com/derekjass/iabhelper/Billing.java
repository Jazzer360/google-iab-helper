package com.derekjass.iabhelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.vending.billing.IInAppBillingService;
import com.derekjass.iabhelper.BillingHelper.BillingError;

public class Billing {

	public static class BillingConnection {

		private static final String RESPONSE_CODE = "RESPONSE_CODE";
		private static final String ITEM_ID_LIST = "ITEM_ID_LIST";
		private static final String DETAILS_LIST = "DETAILS_LIST";
		private static final String INAPP_CONTINUATION_TOKEN = "INAPP_CONTINUATION_TOKEN";
		private static final String INAPP_DATA_SIGNATURE_LIST = "INAPP_DATA_SIGNATURE_LIST";
		private static final String INAPP_PURCHASE_DATA_LIST = "INAPP_PURCHASE_DATA_LIST";
		private static final String BUY_INTENT = "BUY_INTENT";
		private static final String INAPP_DATA_SIGNATURE = "INAPP_DATA_SIGNATURE";
		private static final String INAPP_PURCHASE_DATA = "INAPP_PURCHASE_DATA";

		private Billing mBilling;
		private boolean mConnected = true;

		private BillingConnection(Billing billing) {
			mBilling = billing;
		}

		public void close() {
			mConnected = false;
			mBilling.closeConnection(this);
			mBilling = null;
		}

		private void checkConnected() {
			if (!mConnected) {
				throw new IllegalStateException("BillingConnection was closed");
			}
		}

		public void queryOwnedProducts() {
			getPurchases("inapp");
		}

		public void queryOwnedSubscriptions() {
			getPurchases("subs");
		}

		private void getPurchases(final String type) {
			checkConnected();
			mBilling.mExecutor.execute(new Runnable() {
				@Override
				public void run() {
					try {
						String continuationToken = null;

						do {
							Bundle result = mBilling.mService.getPurchases(3,
									mBilling.mContext.getPackageName(), type,
									continuationToken);

							int resultCode = result.getInt(RESPONSE_CODE);
							if (resultCode != 0) {
								deliverError(BillingError
										.fromResponseCode(resultCode), listener);
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
								boolean valid = true;
								if (mValidator != null) {
									valid = mValidator.validateSignature(json,
											signature);
								}
								if (valid) {
									Purchase purchase = new Purchase(json,
											signature);
									purchases.add(purchase);
								} else {
									deliverError(
											BillingError.INVALID_SIGNATURE,
											listener);
									return;
								}
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

		public void requestProductInfo(String... productIds) {
			// TODO
		}

		public void requestSubscriptionInfo(String... productIds) {
			// TODO
		}

		public void purchaseProduct(String id, String payload) {
			// TODO
		}

		public void purchaseSubscription(String id, String payload) {
			// TODO
		}

		public void consumeProduct(Purchase purchase) {
			// TODO
		}
	}

	private class CleanupThread extends Thread {
		@Override
		public void run() {
			mExecutor.shutdown();
			try {
				mExecutor.awaitTermination(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				mExecutor.shutdownNow();
			}
			mExecutor = null;
			mContext.unbindService(mConnection);
			mContext = null;
			mConnection = null;
			mService = null;
		}
	}

	private static Billing sInstance;
	private static Lock sLock = new ReentrantLock();

	private Context mContext;
	private ServiceConnection mConnection;
	private boolean mBound;
	private IInAppBillingService mService;
	private int mConnections;
	private ExecutorService mExecutor;

	private Billing(Context context) {
		mContext = context.getApplicationContext();
		mConnection = new ServiceConnection() {
			@Override
			public void onServiceDisconnected(ComponentName name) {}

			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				mService = IInAppBillingService.Stub.asInterface(service);
			}
		};
		Intent i = new Intent(
				"com.android.vending.billing.InAppBillingService.BIND");
		mBound = mContext.bindService(i, mConnection, Context.BIND_AUTO_CREATE);
		mExecutor = Executors.newCachedThreadPool();
	}

	private void closeConnection(BillingConnection billingConnection) {
		sLock.lock();
		try {
			mConnections--;
			tryDisconnect();
		} finally {
			sLock.unlock();
		}
	}

	private void tryDisconnect() {
		if (mConnections == 0) {
			sInstance = null;
			new CleanupThread().start();
		}
	}

	public static BillingConnection getConnection(Context context) {
		BillingConnection connection = null;
		sLock.lock();
		try {
			if (sInstance == null) {
				sInstance = new Billing(context);
			}
			if (sInstance.mBound) {
				sInstance.mConnections++;
				connection = new BillingConnection(sInstance);
			} else {
				sInstance.tryDisconnect();
			}
		} finally {
			sLock.unlock();
		}
		return connection;
	}
}
