package com.derekjass.iabhelper;

public interface SignatureValidator {
	public boolean validateSignature(String data, String signature);
}
