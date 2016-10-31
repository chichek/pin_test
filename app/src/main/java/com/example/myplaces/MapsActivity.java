package com.example.myplaces;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import com.example.myplaces.db.PlacesDbHelper;
import com.example.myplaces.model.ClusterMarker;
import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerDragListener, GoogleApiClient.ConnectionCallbacks,
		UserPinsFragment.PinsLoadedCallback, GoogleMap.OnMyLocationButtonClickListener, ClusterManager.OnClusterClickListener<ClusterMarker>, ActionMode.Callback {

	private static final int ACCESS_LOCATION_REQUEST_CODE = 100;
	private static final String USER_PINS_LOADER_FRAGMENT_TAG = "frg_user_pins_loader";
	private static final String KEY_USER_CURRENT_LOCATION = "current_location_key";

	private GoogleMap mMap;
	private ClusterManager<ClusterMarker> mClusterManager;
	private PlacesDbHelper mDbHelper;
	private GoogleApiClient mGoogleApiClient;
	private Location mCurrentLocation;
	private android.view.ActionMode mActionMode;
	private Marker mActiveMarker;
	private ClusterMarker mEditableClasterMarker;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_maps);
		SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
		mapFragment.getMapAsync(this);
		mDbHelper = PlacesDbHelper.getInstance(getApplicationContext());
		if (mGoogleApiClient == null) {
			mGoogleApiClient = new GoogleApiClient.Builder(this)
					.addConnectionCallbacks(this)
					.addApi(LocationServices.API)
					.build();
		}
		if (savedInstanceState != null) {
			mCurrentLocation = savedInstanceState.getParcelable(KEY_USER_CURRENT_LOCATION);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.map_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.switch_user:
				if (AccessToken.getCurrentAccessToken() != null) {
					LoginManager.getInstance().logOut();
					mDbHelper.logoutUser();
					startActivity(new Intent(this, LoginActivity.class));
					finish();
				}
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart() {
		mGoogleApiClient.connect();
		super.onStart();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mCurrentLocation != null) {
			outState.putParcelable(KEY_USER_CURRENT_LOCATION, mCurrentLocation);
		}
	}

	@Override
	protected void onStop() {
		mGoogleApiClient.disconnect();
		super.onStop();
	}

	@Override
	public void onMapReady(GoogleMap googleMap) {
		mMap = googleMap;
		mClusterManager = new ClusterManager<>(this, mMap);
		mClusterManager.setOnClusterClickListener(this);
		mMap.setOnCameraIdleListener(mClusterManager);
		mMap.setOnMarkerDragListener(this);
		mMap.setOnMyLocationButtonClickListener(this);
		if (mCurrentLocation != null) {
			mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
		}
		loadUserPins();
		showMyLocation();
	}

	@Override
	public boolean onMyLocationButtonClick() {
		ClusterMarker pin = new ClusterMarker(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()), null);
		mClusterManager.addItem(pin);
		mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pin.getPosition(), 13));
		mDbHelper.addPin(pin);
		return false;
	}

	@Override
	public void onConnected(@Nullable Bundle bundle) {
		if (hasLocationPermission()) {
			mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
		} else {
			askForLocationPermission();
		}
	}

	@Override
	public void onConnectionSuspended(int i) {
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
		switch (requestCode) {
			case ACCESS_LOCATION_REQUEST_CODE: {
				if (grantResults.length > 0 && permissions[0].equals(android.Manifest.permission.ACCESS_FINE_LOCATION) && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					mMap.setMyLocationEnabled(true);
					mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
				}
				break;
			}
		}
	}

	@Override
	public void onPinsLoaded(List<ClusterMarker> clusterMarkers) {
		if (mClusterManager != null) {
			mClusterManager.clearItems();
			mClusterManager.addItems(clusterMarkers);
			mClusterManager.cluster();
		}
	}

	@Override
	public boolean onClusterClick(Cluster<ClusterMarker> cluster) {
		LatLngBounds.Builder builder = LatLngBounds.builder();
		for (ClusterMarker item : cluster.getItems()) {
			builder.include(item.getPosition());
		}
		final LatLngBounds bounds = builder.build();
		try {
			mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public void onMarkerDragStart(Marker marker) {
		if (mActionMode == null) {
			mActionMode = startActionMode((android.view.ActionMode.Callback) MapsActivity.this);
		}
		mActiveMarker = marker;
		if (mEditableClasterMarker.getPosition() != mActiveMarker.getPosition()) {
			mEditableClasterMarker = mDbHelper.getMarkerByPosition(marker.getPosition());
		}
	}

	@Override
	public void onMarkerDrag(Marker marker) {}

	@Override
	public void onMarkerDragEnd(Marker marker) {
		mEditableClasterMarker.setPosition(marker.getPosition());
		mDbHelper.updatePin(mEditableClasterMarker);
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		mode.getMenuInflater().inflate(R.menu.marker_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_delete_marker:
				mClusterManager.getClusterMarkerCollection().remove(mActiveMarker);
				mDbHelper.deletePin(mEditableClasterMarker.getId());
				mActionMode.finish();
				return true;
		}
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		mActionMode = null;
		mActiveMarker = null;
	}

	private void showMyLocation() {
		if (hasLocationPermission()) {
			mMap.setMyLocationEnabled(true);
		} else {
			askForLocationPermission();
		}
	}

	private void loadUserPins() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		UserPinsFragment fragment = (UserPinsFragment) fragmentManager.findFragmentByTag(USER_PINS_LOADER_FRAGMENT_TAG);
		if (fragment == null) {
			fragment = new UserPinsFragment();
			fragmentManager.beginTransaction().add(fragment, USER_PINS_LOADER_FRAGMENT_TAG).commit();
		}
	}

	private boolean hasLocationPermission() {
		return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private void askForLocationPermission() {
		ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST_CODE);
	}
}
