package panaimin.pdfmarker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class Utility {

	private static Utility _instance = null;
	private static String TAG = "PDFMarker.Utility";

	private DateFormat _ymdFormat = null;
	private DateFormat _hmFormat = null;
	private Context _context = null;
	private Toast _toast = null;
	private SharedPreferences _sp = null;

	public static Utility instance() {
		if (_instance == null)
			throw new RuntimeException("");
		return _instance;
	}

	public static Utility instance(Context context) {
		if (_instance == null)
			_instance = new Utility(context);
		return _instance;
	}

	public String getTimeString() {
		return _hmFormat.format(new Date(System.currentTimeMillis()));
	}

	public long getPref(String key, long def) { return _sp.getLong(key, def); }
	public String getPref(String key, String def) { return _sp.getString(key, def); }
	public int getPref(String key, int def) { return _sp.getInt(key, def); }
	public boolean getPref(String key, boolean def) { return _sp.getBoolean(key, def); }

	public void setPref(String key, String value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putString(key, value);
		editor.apply();
	}

	public void setPref(String key, int value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putInt(key, value);
		editor.apply();
	}

	public void setPref(String key, long value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putLong(key, value);
		editor.apply();
	}

	public void setPref(String key, boolean value) {
		SharedPreferences.Editor editor = _sp.edit();
		editor.putBoolean(key, value);
		editor.apply();
	}

	public void showToast(String s) {
		if(_toast != null)
			_toast.cancel();
		_toast = Toast.makeText(_context, s, Toast.LENGTH_SHORT);
		_toast.show();
	}

	public void showToast(int resId) {
		showToast(_context.getString(resId));
	}

	public boolean isChinese() {
		return Locale.getDefault().getLanguage().equals(Locale.CHINESE.getLanguage());
	}

	public boolean isJapanese() {
		return Locale.getDefault().getLanguage().equals(Locale.JAPANESE.getLanguage());
	}

	private Utility(Context context) {
		_context = context;
		_ymdFormat = DateFormat.getDateInstance();
		_hmFormat = new SimpleDateFormat("H:m");
		_sp = PreferenceManager.getDefaultSharedPreferences(context);
	}

}
