package com.derekjass.android.iabhelper;

public interface SignatureValidator {
    public boolean validateSignature(String data, String signature);
}
