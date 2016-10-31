package com.example.myplaces.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.example.myplaces.model.ClusterMarker;
import com.google.android.gms.maps.model.LatLng;

import java.util.Locale;

public class PlacesDbHelper extends SQLiteOpenHelper {

	private static PlacesDbHelper mInstance;

	public static synchronized PlacesDbHelper getInstance(Context context) {
		if (mInstance == null) {
			mInstance = new PlacesDbHelper(context);
		}
		return mInstance;
	}

	private PlacesDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(SQL_CREATE_USERS);
		db.execSQL(SQL_CREATE_PIN_ENTRIES);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL(SQL_DELETE_TABLES);
		onCreate(db);
	}

	public Cursor getUserPins() {
		return getReadableDatabase().rawQuery(SQL_GET_USER_PINS, null);
	}

	public void loginUser(String facebookId) {
		getWritableDatabase().execSQL(SQL_LOGIN_USER.replace("[facebook_id]", facebookId));
	}

	public String getCurrentUser() {
		Cursor cursor = getReadableDatabase().query(PlacesPinContract.User.TABLE_NAME,
				new String[]{PlacesPinContract.User.COLUMN_NAME_FACEBOOK_ID},
				PlacesPinContract.User.COLUMN_NAME_IS_CURRENT + " = ?",
				new String[]{"1"}, null, null, null);
		String facebookId = null;
		if (cursor != null && cursor.moveToFirst()) {
			facebookId = cursor.getString(0);
			cursor.close();
		}
		return facebookId;
	}

	public void logoutUser() {
		ContentValues values = new ContentValues();
		values.put(PlacesPinContract.User.COLUMN_NAME_IS_CURRENT, 0);
		getWritableDatabase().update(PlacesPinContract.User.TABLE_NAME, values, PlacesPinContract.User.COLUMN_NAME_IS_CURRENT + " = ?", new String[]{"1"});
	}

	public void addPin(ClusterMarker pin) {
		ContentValues values = new ContentValues();
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID, pin.getId());
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE, pin.getPosition().latitude);
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE, pin.getPosition().longitude);
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_USER_ID, "(" + SQL_CURRENT_USER_ID + ")");
		getWritableDatabase().insert(PlacesPinContract.PinEntry.TABLE_NAME, null, values);
	}

	public void updatePin(ClusterMarker pin) {
		ContentValues values = new ContentValues();
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE, pin.getPosition().latitude);
		values.put(PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE, pin.getPosition().longitude);
		getWritableDatabase().update(PlacesPinContract.PinEntry.TABLE_NAME, values, PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID + " = '?' ", new String[]{pin.getId()});
	}

	public ClusterMarker getMarkerByPosition(LatLng position) {
		Cursor cursor = getReadableDatabase().query(PlacesPinContract.PinEntry.TABLE_NAME,
				new String[]{PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID, PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE, PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE},
				String.format(Locale.ENGLISH, "%s = %f AND %s = %f AND %s = (%s)", PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE, position.latitude,
						PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE, position.longitude,
						PlacesPinContract.PinEntry.COLUMN_NAME_USER_ID, SQL_CURRENT_USER_ID), null, null, null, null, "1");
		ClusterMarker marker = null;
		if (cursor != null && cursor.moveToFirst()) {
			double latitude = cursor.getDouble(cursor.getColumnIndex(PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE));
			double longitude = cursor.getDouble(cursor.getColumnIndex(PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE));
			String id = cursor.getString(cursor.getColumnIndex(PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID));
			marker = new ClusterMarker(new LatLng(latitude, longitude), id);
			cursor.close();
		}
		return marker;
	}

	public void deletePin(String pinId) {
		getWritableDatabase().delete(PlacesPinContract.PinEntry.TABLE_NAME, PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID + " = '?'", new String[]{pinId});
	}

	private static final int DATABASE_VERSION = 5;
	private static final String DATABASE_NAME = "PlacesPin.db";


	private static final String SQL_CREATE_USERS =
			"CREATE TABLE IF NOT EXISTS " + PlacesPinContract.User.TABLE_NAME + " (" +
					PlacesPinContract.User.COLUMN_NAME_FACEBOOK_ID + " TEXT PRIMARY KEY, " +
					PlacesPinContract.User.COLUMN_NAME_IS_CURRENT + " INTEGER NOT NULL DEFAULT 0)";

	private static final String SQL_CREATE_PIN_ENTRIES =
			"CREATE TABLE IF NOT EXISTS " + PlacesPinContract.PinEntry.TABLE_NAME + " (" +
					PlacesPinContract.PinEntry.COLUMN_NAME_PIN_ID + " TEXT PRIMARY KEY, " +
					PlacesPinContract.PinEntry.COLUMN_NAME_USER_ID + " TEXT NOT NULL, " +
					PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE + " REAL NOT NULL DEFAULT 0, " +
					PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE + " REAL NOT NULL DEFAULT 0)";

	private static final String SQL_DELETE_TABLES =
			"DROP TABLE IF EXISTS " + PlacesPinContract.User.TABLE_NAME + "; " +
			"DROP TABLE IF EXISTS " + PlacesPinContract.PinEntry.TABLE_NAME;

	private static final String SQL_GET_USER_PINS =
			"SELECT " +
					PlacesPinContract.PinEntry.COLUMN_NAME_LATITUDE + ", " +
					PlacesPinContract.PinEntry.COLUMN_NAME_LONGITUDE +
			" FROM " +
					PlacesPinContract.PinEntry.TABLE_NAME + " pins " +
			"INNER JOIN " + PlacesPinContract.User.TABLE_NAME + " user " +
					"ON user." + PlacesPinContract.User.COLUMN_NAME_FACEBOOK_ID + " = pins." + PlacesPinContract.PinEntry.COLUMN_NAME_USER_ID +
			" WHERE user." + PlacesPinContract.User.COLUMN_NAME_IS_CURRENT + " = 1";

	private static final String SQL_LOGIN_USER =
			"REPLACE INTO " + PlacesPinContract.User.TABLE_NAME +
					" VALUES ('[facebook_id]', 1)";

	private static final String SQL_CURRENT_USER_ID =
			"SELECT " +
					PlacesPinContract.User.COLUMN_NAME_FACEBOOK_ID +
			" FROM " +
			PlacesPinContract.User.TABLE_NAME +
			" WHERE " + PlacesPinContract.User.COLUMN_NAME_IS_CURRENT + " = 1";
}
