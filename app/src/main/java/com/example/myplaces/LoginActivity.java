package com.example.myplaces;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.example.myplaces.db.PlacesDbHelper;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookAuthorizationException;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.Profile;
import com.facebook.ProfileTracker;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

public class LoginActivity extends AppCompatActivity implements FacebookCallback<LoginResult> {

	private CallbackManager mCallbackManager;
	private CoordinatorLayout mLayout;
	private PlacesDbHelper mDbHelper;
	private ProfileTracker mProfileTracker;
	private String mProfileId;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		ProgressBar progressBar = new ProgressBar(this);
		progressBar.setIndeterminate(true);
		progressBar.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setContentView(progressBar);
		FacebookSdk.sdkInitialize(getApplicationContext());
		mDbHelper = PlacesDbHelper.getInstance(getApplicationContext());
		mCallbackManager = CallbackManager.Factory.create();
		if (isUserLoggedIn()) {
			loadMap();
		}
		setContentView(R.layout.act_login);
		mLayout = (CoordinatorLayout) findViewById(R.id.login_layout);
		LoginButton loginButton = (LoginButton) findViewById(R.id.login_button);
		loginButton.registerCallback(mCallbackManager, this);
	}

	@Override
	public void onSuccess(LoginResult loginResult) {
		mProfileTracker = new ProfileTracker() {
			@Override
			protected void onCurrentProfileChanged(Profile oldProfile, Profile currentProfile) {
				mProfileId = currentProfile.getId();
				loadMap();
			}
		};
	}

	@Override
	public void onCancel() {
	}

	@Override
	public void onError(FacebookException error) {
		if (error instanceof FacebookAuthorizationException) {
			Snackbar.make(mLayout, R.string.facebook_login_error, Snackbar.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		mCallbackManager.onActivityResult(requestCode, resultCode, data);
	}

	private void loadMap() {
		if (mProfileId != null) {
			mDbHelper.loginUser(mProfileId);
			Intent intent = new Intent(this, MapsActivity.class);
			startActivity(intent);
			finish();
		}
	}

	private boolean isUserLoggedIn() {
		Profile profile = Profile.getCurrentProfile();
		if (profile != null) {
			mProfileId = profile.getId();
		} else {
			mProfileId = mDbHelper.getCurrentUser();
		}
		AccessToken accessToken = AccessToken.getCurrentAccessToken();
		return (accessToken != null && !accessToken.isExpired()) || !TextUtils.isEmpty(mProfileId);
	}
}
