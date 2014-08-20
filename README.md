# google-iab-helper

An Android library to simplify the usage of Google's in-app billing.

There are several ways in which this library can be used to assist with in-app billing.

## The Fragments
There are a few `PurchaseStateFragment` classes that allow for simple handling of the billing process. Each instance of these fragments handles the billing requests for a single product ID, and may handle either managed products or subscriptions. Every one of these fragments keeps track of the purchase state of the product, though how you handle these state changes varies with the implementation.

Once you have an instance of one of these fragments, there are methods common to them all to initiate various purchase-related requests.
- `getPurchaseState()` - This returns the current purchase state of the product.
- `purchaseProduct(int)` - This starts the purchase process for the user. The `int` passed to this method is used to identify the request made so the result the activity recieves can be matched to the request made. (More on this later)
- `consumePurchase(PurchaseConsumedListener)` - This consumes the product and notifies the associated listener when the consumption was successful.

All of these fragments have a few requirements for them to work properly.

1. They must have arguments supplied to them so they are aware of both the product ID, and the product type. There is a static method in the base class (`PurchaseStateFragment.getArgsBundle(String, String)`) to help with creating the arguments bundle.
2. The activity they reside in must forward the `Intent` it receives from it's `onActivityResult(...)` callback to the fragment via `handleActivityResult(Intent)` so they can update their state after a purchase completes. The result code passed to the callback will identify the request made, and should be checked to see that it matches the request code passed when initially calling `purchaseProduct(int)` as well as check that the result code is `Activity.RESULT_OK` before forwarding the intent.

### SimplePurchaseStateFragment
This is the simplest fragment which has no UI, and may be used without subclassing.

Obtaining an instance of this fragment:
```java
String productId = "your_product_id";
String productType = SimplePurchaseStateFragment.MANAGED_PRODUCT;
Fragment f = SimplePurchaseStateFragment.newInstance(productId, productType);
```
> Alternately, if using subscriptions, simply set `productType` to `SimplePurchaseStateFragment.SUBSCRIPTION`

In addition, your `Activity` the fragment is being attached to must implement `SimplePurchaseStateFragment.PurchaseStateListener`. This is the callback that will be called whenever a change in purchase state occurs. You should handle any changes related to a change in purchase state within this callback.

### PurchaseStateUiFragment
This is a fragment that displays a fragment based on the current purchase state, and automatically changes the fragment as the purchase state changes.

This class has two abstract methods that must be implemented when subclassing.
- `getFragmentForState(PurchaseState)` - This is called when the purchase state has changed and must return a `Fragment` based on the PurchaseState passed.
- `onBillingError(BillingError)` - This is called when an error occured during a billing operation.

A simple implementation may look like this:
```java
public class MyFragment extends PurchaseStateUiFragment {

	@Override
	protected Fragment getFragmentForState(PurchaseState state) {
		switch (state) {
		case NOT_PURCHASED:
			return new NotPurchasedFragment();
			break;
		case PURCHASED:
			return new PurchasedFragment();
			break;
		}
	}
	
	@Override
	protected void onBillingError(BillingError error) {
		Log.w("MyFragment", error.toString());
	}
}
```

Recommendation: When subclassing, it's a good idea to provide a static `newInstance()` method that sets the arguments of the fragment and returns the newly created fragment with the arguments set. Adding to the above example:
```java
public static MyFragment newInstance() {
	MyFragment f = new MyFragment();
	Bundle args = getArgsBundle("your_product_id", MANAGED_PRODUCT);
	f.setArgs(args);
	return f;
}
```
### PurchaseStateFragment
Coming Soon

## BillingHelper
Coming Soon

## (Optional) Signature Validation
Coming Soon
