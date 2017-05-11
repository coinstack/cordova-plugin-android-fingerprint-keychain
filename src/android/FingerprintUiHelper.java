/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.cordova.plugin.android.fingerprintkey;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.support.v4.os.CancellationSignal;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * Small helper class to manage text/icon around fingerprint authentication UI.
 */
@TargetApi(7)
public class FingerprintUiHelper extends FingerprintManagerCompat.AuthenticationCallback {

    static final long ERROR_TIMEOUT_MILLIS = 1600;
    static final long SUCCESS_DELAY_MILLIS = 1300;

    private final Context mContext;
    private final FingerprintManagerCompat mFingerprintManager;
    private final ImageView mIcon;
    private final TextView mErrorTextView;
    private final Callback mCallback;
    private CancellationSignal mCancellationSignal;
    private int mAttempts = 0;

    boolean mSelfCancelled;
    private FingerprintScanner.Locale locale;

    public void setLocale(FingerprintScanner.Locale locale) {
        this.locale = locale;
    }

    /**
     * Builder class for {@link FingerprintUiHelper} in which injected fields from Dagger
     * holds its fields and takes other arguments in the {@link #build} method.
     */
    public static class FingerprintUiHelperBuilder {
        private final FingerprintManagerCompat mFingerPrintManager;
        private final Context mContext;

        public FingerprintUiHelperBuilder(Context context, FingerprintManagerCompat fingerprintManager) {
            mFingerPrintManager = fingerprintManager;
            mContext = context;
        }

        public FingerprintUiHelper build(ImageView icon, TextView errorTextView, Callback callback) {
            return new FingerprintUiHelper(mContext, mFingerPrintManager, icon, errorTextView,
                    callback);
        }
    }

    /**
     * Constructor for {@link FingerprintUiHelper}. This method is expected to be called from
     * only the {@link FingerprintUiHelperBuilder} class.
     */
    private FingerprintUiHelper(Context context, FingerprintManagerCompat fingerprintManager,
                                ImageView icon, TextView errorTextView, Callback callback) {
        mFingerprintManager = fingerprintManager;
        mIcon = icon;
        mErrorTextView = errorTextView;
        mCallback = callback;
        mContext = context;
    }

    public boolean isFingerprintAuthAvailable() {
        return mFingerprintManager.isHardwareDetected()
                && mFingerprintManager.hasEnrolledFingerprints();
    }

    public boolean isHardwareDetected() {
        return mFingerprintManager.isHardwareDetected();
    }

    public boolean hasEnrolledFingerprints() {
        return mFingerprintManager.hasEnrolledFingerprints();
    }

    public void startListening(FingerprintManagerCompat.CryptoObject cryptoObject) {
        if (!isFingerprintAuthAvailable()) {
            return;
        }
        mCancellationSignal = new CancellationSignal();
        mSelfCancelled = false;
        mFingerprintManager
                .authenticate(cryptoObject, 0 /* flags */, mCancellationSignal, this, null);

        int ic_fp_40px_id = mContext.getResources()
                .getIdentifier("ic_fp_40px", "drawable", FingerprintScanner.packageName);
        mIcon.setImageResource(ic_fp_40px_id);
    }

    public void stopListening() {
        if (mCancellationSignal != null) {
            mSelfCancelled = true;
            mCancellationSignal.cancel();
            mCancellationSignal = null;
        }
    }

    @Override
    public void onAuthenticationError(int errMsgId, final CharSequence errString) {
        if (!mSelfCancelled) {
            showError(errMsgId, errString);
            final int errMsgIdFinal = errMsgId;
            mIcon.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCallback.onError(errMsgIdFinal);
                }
            }, ERROR_TIMEOUT_MILLIS);
        }
    }

    @Override
    public void onAuthenticationHelp(int helpMsgId, CharSequence helpString) {
        showError(helpMsgId, helpString);
    }

    @Override
    public void onAuthenticationFailed() {
        mAttempts++;
        int fingerprint_not_recognized_id = mContext.getResources()
                .getIdentifier("fingerprint_not_recognized", "string",
                        FingerprintScanner.packageName);
        if (this.locale != null) {
            showError(0, this.locale.notRecognizedText);
        } else {
            showError(0, mIcon.getResources().getString(
                    fingerprint_not_recognized_id));
        }
    }

    public int getAttempts() {
        return mAttempts;
    }

    @Override
    public void onAuthenticationSucceeded(FingerprintManagerCompat.AuthenticationResult result) {
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        int ic_fp_40px_id = mContext.getResources()
                    .getIdentifier("ic_fp_40px", "drawable", FingerprintScanner.packageName);
        mIcon.setImageResource(ic_fp_40px_id);


        int success_color_id = mContext.getResources()
                .getIdentifier("success_color", "color", FingerprintScanner.packageName);
        mErrorTextView.setTextColor(
                mErrorTextView.getResources().getColor(success_color_id, null));
        int fingerprint_success_id = mContext.getResources()
                .getIdentifier("fingerprint_success", "string", FingerprintScanner.packageName);
        if (this.locale != null) {
            mErrorTextView.setText(this.locale.successText);
        } else {
            mErrorTextView.setText(
                    mErrorTextView.getResources().getString(fingerprint_success_id));
        }
        mIcon.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallback.onAuthenticated();
            }
        }, SUCCESS_DELAY_MILLIS);
    }

    private void showError(int errCode, CharSequence error) {
        int ic_fp_40px_id = mContext.getResources()
            .getIdentifier("ic_fp_fail_40px", "drawable", FingerprintScanner.packageName);
        mIcon.setImageResource(ic_fp_40px_id);
        
        switch (errCode) {
            case 7: 
                mErrorTextView.setText(locale.tooManyTries);
                break;
            default:
                mErrorTextView.setText(error);
        }

        int warning_color_id = mContext.getResources()
                .getIdentifier("warning_color", "color", FingerprintScanner.packageName);
        mErrorTextView.setTextColor(
                mErrorTextView.getResources().getColor(warning_color_id, null));
        mErrorTextView.removeCallbacks(mResetErrorTextRunnable);
        mErrorTextView.postDelayed(mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
    }

    Runnable mResetErrorTextRunnable = new Runnable() {
        @Override
        public void run() {
            int hint_color_id = mContext.getResources().getIdentifier("hint_color", "color", FingerprintScanner.packageName);
             mErrorTextView.setTextColor(mErrorTextView.getResources().getColor(hint_color_id, null));

            int fingerprint_hint_id = mContext.getResources()
                    .getIdentifier("fingerprint_hint", "string", FingerprintScanner.packageName);
            if (locale != null) {
                mErrorTextView.setText(locale.hintText);
            } else {
                mErrorTextView.setText(
                        mErrorTextView.getResources().getString(fingerprint_hint_id));
            }
            int ic_fp_40px_id = mContext.getResources()
                    .getIdentifier("ic_fp_40px", "drawable", FingerprintScanner.packageName);
            mIcon.setImageResource(ic_fp_40px_id);
        }
    };

    public interface Callback {

        void onAuthenticated();

        void onError(int errCode);
    }
}
