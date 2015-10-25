package panaimin.pdfmarker;

import android.app.Application;
import android.widget.Toast;

public class PDFMarkerApp extends Application {

	private static PDFMarkerApp		_instance;

	static final String PREF_SHOW_LICENSE = "SHOW_LICENSE";

	@Override
	public void onCreate() {
		super.onCreate();
		_instance = this;
		Utility.instance(_instance);
		LogDog.instance().init(this);
	}
	
	@Override
	public void onTerminate() {
		LogDog.instance().close();
		super.onTerminate();
	}

	public static PDFMarkerApp instance() {
		return _instance;
	}
	
}
