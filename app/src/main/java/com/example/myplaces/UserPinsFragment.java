package com.example.myplaces;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;

import com.example.myplaces.db.PlacesDbHelper;
import com.example.myplaces.db.PlacesPinContract;
import com.example.myplaces.model.ClusterMarker;
import com.google.android.gms.maps.model.LatLng;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class UserPinsFragment extends Fragment {

	private PinsLoadedCallback mPinsLoadedCallback;

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		mPinsLoadedCallback = (PinsLoadedCallback) context;
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		new GetUserPins(PlacesDbHelper.getInstance(getActivity().getApplicationContext())).execute();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mPinsLoadedCallback = null;
	}

	public interface PinsLoadedCallback {
		void onPinsLoaded(List<ClusterMarker> clusterMarkers);
	}

	private class GetUserPins extends AsyncTask<Void, Void, List<ClusterMarker>> {

		private WeakReference<PlacesDbHelper> mHelper;

		public GetUserPins(PlacesDbHelper helper) {
			mHelper = new WeakReference<>(helper);
		}

		@Override
		protected List<ClusterMarker> doInBackground(Void... params) {
			PlacesDbHelper helper = mHelper.get();
			List<ClusterMarker> pins = new ArrayList<>();
			if (helper != null) {
				Cursor cursor = helper.getUserPins();
				if (cursor != null && cursor.moveToFirst()) {
					while (cursor.moveToNext()) {
						pins.add(new ClusterMarker(new LatLng(cursor.getDouble(cursor.getColumnIndex(PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE)),
								cursor.getDouble(cursor.getColumnIndex(PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE))), null));
					}
					cursor.close();
				}
			}
			return pins;
		}

		@Override
		protected void onPostExecute(List<ClusterMarker> clusterMarkers) {
			super.onPostExecute(clusterMarkers);
			if (mPinsLoadedCallback != null) {
				mPinsLoadedCallback.onPinsLoaded(clusterMarkers);
			}
		}
	}
}
