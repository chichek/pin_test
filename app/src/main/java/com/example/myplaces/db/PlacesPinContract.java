package com.example.myplaces.db;

import android.provider.BaseColumns;

public final class PlacesPinContract {

	private PlacesPinContract() {
	}

	public static class User implements BaseColumns {
		public static final String TABLE_NAME = "tblUsers";
		public static final String COLUMN_NAME_FACEBOOK_ID = "facebook_id";
		public static final String COLUMN_NAME_IS_CURRENT = "is_current";
	}

	public static class PinEntry implements BaseColumns {
		public static final String TABLE_NAME = "tblPinsByUser";
		public static final String COLUMN_NAME_PIN_ID = "pin_id";
		public static final String COLUMN_NAME_USER_ID = "user_id";
		public static final String COLUMN_NAME_LATITUDE = "latitude";
		public static final String COLUMN_NAME_LONGITUDE = "longitude";
	}
}
