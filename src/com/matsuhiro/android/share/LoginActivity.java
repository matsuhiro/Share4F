package com.matsuhiro.android.share;

import com.facebook.FacebookActivity;
import com.facebook.SessionState;

import android.os.Bundle;
import android.util.Log;

public class LoginActivity extends FacebookActivity {

	private static final String TAG = LoginActivity.class.getSimpleName();
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.login_activity);
		this.setResult(RESULT_CANCELED);
	}

	@Override
	protected void onSessionStateChange(SessionState state, Exception exception) {
		super.onSessionStateChange(state, exception);
		if (state == SessionState.OPENING) {
			Log.d(TAG, "OPENING");
		} else if (state == SessionState.OPENED_TOKEN_UPDATED) {
			Log.d(TAG, "OPENED_TOKEN_UPDATED");
		} else if (state == SessionState.OPENED) {
			Log.d(TAG, "OPENED");
			this.setResult(RESULT_OK);
			finish();
		} else if (state == SessionState.CREATED_TOKEN_LOADED) {
			Log.d(TAG, "CREATED_TOKEN_LOADED");
		} else if (state == SessionState.CREATED) {
			Log.d(TAG, "CREATED");
		} else if (state == SessionState.CLOSED_LOGIN_FAILED) {
			Log.d(TAG, "CLOSED_LOGIN_FAILED");
		} else if (state == SessionState.CLOSED) {
			Log.d(TAG, "CLOSED");
		}
	}
}
