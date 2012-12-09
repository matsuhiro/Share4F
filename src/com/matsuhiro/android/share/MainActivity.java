package com.matsuhiro.android.share;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.facebook.FacebookActivity;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.Request;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.SessionLoginBehavior;
import com.facebook.SessionState;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;

public class MainActivity extends FacebookActivity {
	private static final String TAG = MainActivity.class.getSimpleName();
	private static final List<String> PERMISSIONS = new ArrayList<String>() {
		{
			add("publish_actions");
		}
	};

	private static final int REQUEST_LOGIN = 1;
	private static final int REQUEST_TAKE_PICTURE = 2;
	private static final int REAUTHORIZE_ACTIVITY = 3;
	private final String PENDING_ACTION_BUNDLE_KEY = "com.matsuhiro.android.share:PendingAction";
	private final String PENDING_ACTION_FILE_NAME_KEY = "com.matsuhiro.android.share:File";

	private String mFileName;

	private PendingAction pendingAction = PendingAction.NONE;

	private enum PendingAction {
		NONE, POST_PHOTO
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");

		if (getOpenSession() != null && savedInstanceState == null) {
			// start camera
			startCamera();
		} else {
			// start login activity
			Intent intent = new Intent(this, LoginActivity.class);
			Log.d(TAG, "start login");
			startActivityForResult(intent, REQUEST_LOGIN);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		Log.d(TAG, "onSaveInstanceState");

		outState.putInt(PENDING_ACTION_BUNDLE_KEY, pendingAction.ordinal());
		outState.putString(PENDING_ACTION_FILE_NAME_KEY, mFileName);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		Log.d(TAG, "onRestoreInstanceState");

		int ordinal = savedInstanceState.getInt(PENDING_ACTION_BUNDLE_KEY, 0);
		pendingAction = PendingAction.values()[ordinal];

		mFileName = savedInstanceState.getString(PENDING_ACTION_FILE_NAME_KEY);
	}

	@Override
	protected void onSessionStateChange(SessionState state, Exception exception) {
		super.onSessionStateChange(state, exception);
		Log.d(TAG, "onSessionStateChange");
		if (pendingAction != PendingAction.NONE
				&& exception instanceof FacebookOperationCanceledException) {
			new AlertDialog.Builder(this).setTitle("canceled")
					.setMessage("permission").setPositiveButton("OK", null)
					.show();
			pendingAction = PendingAction.NONE;
		} else if (state == SessionState.OPENED_TOKEN_UPDATED) {
			handlePendingAction();
		}

	}

	private void startCamera() {
		Intent intent = new Intent();
		intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			String fileName = DateFormat.format("yyyy-MM-dd_kk.mm.ss",
					System.currentTimeMillis()).toString()
					+ ".jpg";

			mFileName = fileName;
			File savedImageFile = new File(imageDir(), fileName);

			if (!savedImageFile.exists()) {
				try {
					savedImageFile.createNewFile();
					Uri imageUri = Uri.fromFile(savedImageFile);
					intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
					Log.d(TAG, "start to take a picture");
					this.startActivityForResult(intent, REQUEST_TAKE_PICTURE);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			// show set sd card
		}
	}

	private String imageDir() {
		File imageDirectory = new File(
				Environment.getExternalStorageDirectory(), "matsuhiroshare");
		imageDirectory.mkdir();
		return imageDirectory.getAbsolutePath();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d(TAG, "requestCode = " + requestCode);
		Log.d(TAG, "resultCode = " + resultCode);
		Log.d(TAG, "data = " + data);
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_LOGIN:
			handleRequestLogin(resultCode, data);
			break;
		case REQUEST_TAKE_PICTURE:
		case REAUTHORIZE_ACTIVITY:
			handleResultCamera(resultCode, data);
			break;
		}
	}

	private void handleRequestLogin(int resultCode, Intent data) {
		Log.d(TAG, "handle login resultCode = " + resultCode);
		if (resultCode == Activity.RESULT_OK) {
			// start camera
			startCamera();
		} else {
			finish();
			MainActivity.this.moveTaskToBack(true);
		}
	}

	private void handleResultCamera(int resultCode, Intent data) {
		Log.d(TAG, "handle camera resultCode = " + resultCode);
		if (resultCode == Activity.RESULT_OK) {
			// start upload fb
			performPublish(PendingAction.POST_PHOTO);
		} else {
			// show yes/no dialog
			finish();
			MainActivity.this.moveTaskToBack(true);
		}
	}

	private void handlePendingAction() {
		PendingAction previouslyPendingAction = pendingAction;
		// These actions may re-set pendingAction if they are still pending, but
		// we assume they
		// will succeed.
		pendingAction = PendingAction.NONE;

		switch (previouslyPendingAction) {
		case POST_PHOTO:
			try {
				File savedImageFile = new File(imageDir(), mFileName);
				postPhoto(savedImageFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			break;
		}
	}

	private void performPublish(PendingAction action) {
		Session session = Session.getActiveSession();
		if (session != null) {
			pendingAction = action;
			if (hasPublishPermission()) {
				// We can do the action right away.
				handlePendingAction();
			} else {
				// We need to reauthorize, then complete the action when we get
				// called back.
				Session.ReauthorizeRequest reauthRequest = new Session.ReauthorizeRequest(
						this, PERMISSIONS).setRequestCode(REAUTHORIZE_ACTIVITY)
						.setLoginBehavior(
								SessionLoginBehavior.SSO_WITH_FALLBACK);
				session.reauthorizeForPublish(reauthRequest);
			}
		}
	}

	private void postPhoto(File file) throws FileNotFoundException {
		if (hasPublishPermission() && file.exists()) {
			Log.d(TAG, "post photo " + file.toString());
			Toast.makeText(MainActivity.this,
					"started photo uploading.", Toast.LENGTH_LONG)
					.show();
			MainActivity.this.finish();
			Request request = Request.newUploadPhotoRequest(
					Session.getActiveSession(), file, new Request.Callback() {

						@Override
						public void onCompleted(Response response) {
							Log.d(TAG, "post photo response " + response);
							Toast.makeText(MainActivity.this,
									"photo is uploaded.", Toast.LENGTH_LONG)
									.show();
							MainActivity.this.finish();
						}

					});
			Request.executeBatchAsync(request);
			MainActivity.this.moveTaskToBack(true);
		} else {
			pendingAction = PendingAction.POST_PHOTO;
		}
	}

	private Session getOpenSession() {
		Session openSession = getSession();
		if (openSession != null && openSession.isOpened()) {
			return openSession;
		}
		return null;
	}

	private boolean hasPublishPermission() {
		Session session = Session.getActiveSession();
		return session != null
				&& session.getPermissions().contains("publish_actions");
	}
}
