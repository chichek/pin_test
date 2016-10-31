package com.example.myplaces.model;

import android.text.TextUtils;

import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.clustering.ClusterItem;

import java.util.UUID;

public class ClusterMarker implements ClusterItem {
	private LatLng mPosition;
	private String mId ;

	public ClusterMarker(LatLng latLng, String id) {
		this.mPosition = latLng;
		this.mId = TextUtils.isEmpty(id) ? UUID.randomUUID().toString() : id;
	}

	public String getId() {
		return mId;
	}

	@Override
	public LatLng getPosition() {
		return mPosition;
	}

	public void setPosition(LatLng mPosition) {
		this.mPosition = mPosition;
	}
}
