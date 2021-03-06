package panaimin.pdfmarker;

/***
 Copyright (C) <2015>  <Aimin Pan>

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

class Utility {

	private static Utility _instance = null;
	private static String TAG = "PDFMarker.Utility";

	private DateFormat _ymdFormat = null;
	private DateFormat _hmFormat = null;
	private Context _context = null;
	private Toast _toast = null;
	private SharedPreferences _sp = null;

	static Utility instance() {
		if (_instance == null)
			throw new RuntimeException("");
		return _instance;
	}

	static Utility instance(Context context) {
		if (_instance == null)
			_instance = new Utility(context);
		return _instance;
	}

	String getTimeString() {
		return _hmFormat.format(new Date(System.currentTimeMillis()));
	}

	long getPref(String key, long def) { return _sp.getLong(key, def); }
	String getPref(String key, String def) { return _sp.getString(key, def); }
	int getPref(String key, int def) { return _sp.getInt(key, def); }
	boolean getPref(String key, boolean def) { return _sp.getBoolean(key, def); }

	void setPref(String key, String value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putString(key, value);
		editor.apply();
	}

	void setPref(String key, int value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	void setPref(String key, long value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putLong(key, value);
		editor.apply();
	}

	void setPref(String key, boolean value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putBoolean(key, value);
		editor.apply();
	}

	void showToast(String s) {
		if(_toast != null)
			_toast.cancel();
		_toast = Toast.makeText(_context, s, Toast.LENGTH_SHORT);
		_toast.show();
	}

	void showToast(int resId) {
		showToast(_context.getString(resId));
	}

	boolean isChinese() {
		return Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
	}

	boolean isJapanese() {
		return Locale.getDefault().getLanguage().equals(Locale.JAPANESE.getLanguage());
	}

	private Utility(Context context) {
		_context = context;
		_ymdFormat = DateFormat.getDateInstance();
		_hmFormat = new SimpleDateFormat("H:m");
		_sp = PreferenceManager.getDefaultSharedPreferences(context);
	}

}
