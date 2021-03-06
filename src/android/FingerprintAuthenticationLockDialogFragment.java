package com.cordova.plugin.android.fingerprintkey;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.hardware.fingerprint.FingerprintManagerCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
public class FingerprintAuthenticationLockDialogFragment extends DialogFragment
        implements FingerprintUiHelper.Callback {

    private static final String TAG = "FingerprintAuthDialog";
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 1;

    private Button mCancelButton;
    private Button mSecondDialogButton;
    private View mFingerprintContent;

    private Stage mStage = Stage.FINGERPRINT;

    private KeyguardManager mKeyguardManager;
    private FingerprintManagerCompat.CryptoObject mCryptoObject;
    private FingerprintUiHelper mFingerprintUiHelper;
    FingerprintUiHelper.FingerprintUiHelperBuilder mFingerprintUiHelperBuilder;
    private Callback callback;
    private FingerprintScanner.Locale locale = null;
    private TimerTask mTask;
    private Timer mTimer;
    private int remaining;
    private int waitTime;

    public FingerprintAuthenticationLockDialogFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Do not create a new Fragment when the Activity is re-created such as orientation changes.
        setRetainInstance(true);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Material_Light_Dialog);

        mKeyguardManager = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        mFingerprintUiHelperBuilder = new FingerprintUiHelper.FingerprintUiHelperBuilder(
                getContext(), FingerprintManagerCompat.from(getContext()));

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle args = getArguments();
        Log.d(TAG, "disableBackup: " + FingerprintScanner.mDisableBackup);

        int fingerprint_dialog_container_id = getResources()
                .getIdentifier("fingerprint_lock_dialog_container", "layout",
                        FingerprintScanner.packageName);
        View v = inflater.inflate(fingerprint_dialog_container_id, container, false);
        
       int fingerprint_title_id = getResources()
                    .getIdentifier("fingerprint_auth_lock_dialog_title", "id", FingerprintScanner.packageName);
        TextView mFingerprintTitle = (TextView) v.findViewById(fingerprint_title_id);

        if (this.locale != null) {
             mFingerprintTitle.setText(this.locale.titleText);
            getDialog().setTitle(this.locale.titleText);
        } else {
            mFingerprintTitle.setText(this.locale.titleText);
            int fingerprint_auth_dialog_title_id = getResources()
                    .getIdentifier("fingerprint_auth_lock_dialog_title", "string",
                            FingerprintScanner.packageName);
            mFingerprintTitle.setText(getString(fingerprint_auth_dialog_title_id));
            getDialog().setTitle(getString(fingerprint_auth_dialog_title_id));
        }
        mFingerprintTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);


        getDialog().setCanceledOnTouchOutside(false);
        setCancelable(false);

        int cancel_button_id = getResources()
                .getIdentifier("lock_cancel_button", "id", FingerprintScanner.packageName);
        mCancelButton = (Button) v.findViewById(cancel_button_id);
        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callback.onCancel();
                dismiss();
            }
        });
        int hint_color_id = getContext().getResources().getIdentifier("hint_color", "color", FingerprintScanner.packageName);
        mCancelButton.setTextColor(mCancelButton.getResources().getColor(hint_color_id, null));

        int fingerprint_container_id = getResources()
                .getIdentifier("fingerprint_lock_container", "id", FingerprintScanner.packageName);
        mFingerprintContent = v.findViewById(fingerprint_container_id);

        int fingerprint_icon_id = getResources()
                .getIdentifier("fingerprint_lock_icon", "id", FingerprintScanner.packageName);
        int fingerprint_status_id = getResources()
                .getIdentifier("fingerprint_lock_status", "id", FingerprintScanner.packageName);
        mFingerprintUiHelper = mFingerprintUiHelperBuilder.build(
                (ImageView) v.findViewById(fingerprint_icon_id),
                (TextView) v.findViewById(fingerprint_status_id), this);

        if (this.locale != null) {
            int fingerprint_description_id = getResources()
                    .getIdentifier("fingerprint_lock_description", "id", FingerprintScanner.packageName);
            TextView mFingerprintDescription = (TextView) v.findViewById(fingerprint_description_id);
            mFingerprintDescription.setText(this.locale.descText);

            int fingerprint_hint_id = getResources()
                    .getIdentifier("fingerprint_lock_status", "id", FingerprintScanner.packageName);
            TextView mFingerprintHint = (TextView) v.findViewById(fingerprint_hint_id);
            mFingerprintHint.setText(this.locale.hintText);
            mCancelButton.setText(this.locale.cancelText);

            mFingerprintUiHelper.setLocale(this.locale);
        }

        // fire up timer
        remaining = waitTime;


        int fingerprint_hint_id = getResources()
            .getIdentifier("fingerprint_lock_status", "id", FingerprintScanner.packageName);
        final TextView mFingerprintHint = (TextView) v.findViewById(fingerprint_hint_id);
        final Activity activity = getActivity();
        final String suffix = "초 후에 재시도 해주시기 바랍니다.";
        mTask = new TimerTask() {
            @Override
            public void run() {
                activity.runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        mFingerprintHint.setText("" + remaining + suffix);
                        remaining--;
                        if (remaining == 0) {
                            // quit
                            callback.onSuccess();
                            dismiss();
                            mTimer.cancel();
                            mTimer.purge();
                        }
                    }
                });
            }
        };
         
        mTimer = new Timer();
         
        mTimer.schedule(mTask, 1000, 1000);
        updateStage();
        return v;
    }

    private void updateStage() {
        int cancel_id = getResources()
                .getIdentifier("cancel", "string", FingerprintScanner.packageName);
        switch (mStage) {
            case FINGERPRINT:
                if (this.locale != null) {
                    mCancelButton.setText(this.locale.cancelText);
                } else {
                    mCancelButton.setText(cancel_id);
                }
                mFingerprintContent.setVisibility(View.VISIBLE);
                break;
        }
    }

    public void setWaitTime(int time) {
        this.waitTime = time;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    public void setStage(Stage stage) {
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void onAuthenticated() {
    }

    @Override
    public void onError(int errCode) {
    }

    @Override
    public void onCancel(DialogInterface dialog) {
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setLocale(FingerprintScanner.Locale locale) {
        this.locale = locale;
    }

    /**
     * Enumeration to indicate which authentication method the user is trying to authenticate with.
     */
    public enum Stage {
        FINGERPRINT
    }

    public interface Callback {
        void onSuccess();

        void onError(int errCode);

        void onCancel();
    }
}
