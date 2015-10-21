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
	}

	public static PDFMarkerApp instance() {
		return _instance;
	}
	
	public void showToast(String s) {
		if(_toast != null)
			_toast.cancel();
		_toast = Toast.makeText(this, s, Toast.LENGTH_SHORT);
		_toast.show();
	}
	
	Toast		_toast = null;
}
