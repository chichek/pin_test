package com.example.myplaces;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import com.example.myplaces.db.PlacesDbHelper;
import com.example.myplaces.model.ClusterMarker;
import com.facebook.AccessToken;
import com.facebook.login.LoginManager;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.algo.GridBasedAlgorithm;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerDragListener,
		GoogleApiClient.ConnectionCallbacks, LocationListener, UserPinsFragment.PinsLoadedCallback,
		GoogleMap.OnMyLocationButtonClickListener, ClusterManager.OnClusterClickListener<ClusterMarker> {

	private static final int ACCESS_LOCATION_REQUEST_CODE = 100;
	private static final String USER_PINS_LOADER_FRAGMENT_TAG = "frg_user_pins_loader";
	private static final String KEY_USER_CURRENT_LOCATION = "current_location_key";

	private GoogleMap mMap;
	private ClusterManager<ClusterMarker> mClusterManager;
	private PlacesDbHelper mDbHelper;
	private String mProfileId;
	private GoogleApiClient mGoogleApiClient;
	private LocationRequest mLocationRequest;
	private Location mCurrentLocation;
	private android.view.ActionMode mActionMode;
	private ClusterMarker mEditableClasterMarker;
	private Marker mActiveMarker;
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
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
					mClusterManager.removeItem(mEditableClasterMarker);
					mClusterManager.getMarkerManager().remove(mActiveMarker);
					mDbHelper.deletePin(mEditableClasterMarker.getId());
					mActionMode.finish();
					return true;
			}
			return false;
		}

		@Override
		public void onDestroyActionMode(ActionMode mode) {
			mActionMode = null;
			mEditableClasterMarker = null;
			mActiveMarker = null;
		}
	};

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
		requestLocation();
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			mProfileId = extras.getString(LoginActivity.KEY_FACEBOOK_PROFILE_ID);
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
	protected void onResume() {
		super.onResume();
		loadUserPins();
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
		mClusterManager.setAlgorithm(new GridBasedAlgorithm<ClusterMarker>());
		mClusterManager.setRenderer(new DefaultClusterRenderer<ClusterMarker>(getApplicationContext(), mMap, mClusterManager) {
			@Override
			protected void onBeforeClusterItemRendered(ClusterMarker item, MarkerOptions markerOptions) {
				super.onBeforeClusterItemRendered(item, markerOptions);
				markerOptions.draggable(true);
			}

			@Override
			protected void onClusterItemRendered(ClusterMarker clusterItem, Marker marker) {
				marker.setTag(clusterItem.getId());
				super.onClusterItemRendered(clusterItem, marker);
			}
		});
		mMap.setOnCameraIdleListener(mClusterManager);
		mMap.setOnMarkerDragListener(this);
		mMap.setOnMyLocationButtonClickListener(this);
		if (mCurrentLocation != null) {
			mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
		}
		showMyLocation();
	}

	@Override
	public boolean onMyLocationButtonClick() {
		ClusterMarker pin = new ClusterMarker(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude()), null);
		mDbHelper.addPin(pin, mProfileId);
		mClusterManager.addItem(pin);
		mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pin.getPosition(), 13));
		return false;
	}

	@Override
	public void onConnected(@Nullable Bundle bundle) {
		if (hasLocationPermission()) {
			mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
			if (mCurrentLocation == null) {
				LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
			}
		} else {
			askForLocationPermission();
		}
	}

	@Override
	public void onConnectionSuspended(int i) {
	}

	@Override
	public void onLocationChanged(Location location) {
		mCurrentLocation = location;
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
			mActionMode = startActionMode(mActionModeCallback);
		}
		mActiveMarker = marker;
		if (mEditableClasterMarker == null || mEditableClasterMarker.getPosition() != marker.getPosition()) {
			mEditableClasterMarker = mDbHelper.getMarkerById((String)marker.getTag());
		}
	}

	@Override
	public void onMarkerDrag(Marker marker) {
	}

	@Override
	public void onMarkerDragEnd(Marker marker) {
		mEditableClasterMarker.setPosition(marker.getPosition());
		mDbHelper.updatePin(mEditableClasterMarker);
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

	private void requestLocation() {
		mLocationRequest = LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(10000)
				.setFastestInterval(5000);
	}

	private boolean hasLocationPermission() {
		return ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
	}

	private void askForLocationPermission() {
		ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_LOCATION_REQUEST_CODE);
	}
}
