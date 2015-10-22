package panaimin.pdfmarker;

import android.app.Application;
import android.widget.Toast;

public class PDFMarkerApp extends Application {

	private static PDFMarkerApp		_instance;

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
