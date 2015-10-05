package panaimin.pdfmarker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
//import android.util.Log;
import android.util.Log;

public class LogDog implements UncaughtExceptionHandler {
	
	public static String 			TAG = "LogDog";
	
	private static HashSet<String>	_tags = new HashSet<String>();
	private static boolean			_debug = false;
	
	static {
		_tags.add(PDFMaster.TAG);
		_tags.add(RecentlyUsedActivity.TAG);
		_tags.add(DB.TAG);
	}

	public static LogDog instance() {
		return _instance;
	}
	
	public static void i(String tag, String message) {
		if(_debug) {
			Log.i(tag, message);
			return;
		}
		PrintWriter writer = _instance.getWriter();
		if(writer != null && _tags.contains(tag)) {
			_instance._writer.println("i:" + tag + ":" + message);
		}
	}

	public static void e(String tag, String message) {
		if(_debug) {
			Log.i(tag, message);
			return;
		}
		PrintWriter writer = _instance.getWriter();
		if(writer != null && _tags.contains(tag)) {
			_instance._writer.println("e" + tag + ":" + message);
		}
	}

	public static void w(String tag, String message) {
		if(_debug) {
			Log.i(tag, message);
			return;
		}
		PrintWriter writer = _instance.getWriter();
		if(writer != null && _tags.contains(tag)) {
			_instance._writer.println("w" + tag + ":" + message);
		}
	}

	public void init(Context context) {
		_context = context;
		_defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler(this);
	}
	
	public void close() {
		if(_writer != null) {
			_writer.close();
			_writer = null;
		}
	}
	
	// private
	
	private static LogDog					_instance = new LogDog();
	private static final String 			PATH =
		Environment.getExternalStorageDirectory().getPath() + "/log";
	private static final String				FILE_NAME = "PDFMarker.log";
	private Context							_context;
	private Thread.UncaughtExceptionHandler	_defaultHandler;
	private PrintWriter						_writer = null;
	
	private LogDog() {
	}
	
	private PrintWriter getWriter() {
		if(_writer != null)
			return _writer;
		// check if we have SD card
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			//Log.w(TAG, "sdcard unmounted,skip dump exception");
			return null;
		}
		File dir = new File(PATH);
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File file = new File(PATH + "/" + FILE_NAME);
		try {
			_writer = new PrintWriter(new BufferedWriter(new FileWriter(file)));
		} catch (IOException e) {
			//Log.e(TAG, "Failed to open " + PATH + "/" + FILE_NAME);
			_writer = null;
		}
		return _writer;
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		// first display a toast message
		new Thread() {
			@Override
			public void run() {
				Looper.prepare();
				PDFMarkerApp.instance().showToast("Sorry for the trouble, dumping uncaught exeption to SD card");
			}
		}.start();
		try {
			dump(ex);
		} catch (Exception e) {
			//Log.e(TAG, "Failed to dump because " + e.getMessage());
			e.printStackTrace();
		}
		if (_defaultHandler != null) {  
			_defaultHandler.uncaughtException(thread, ex);  
		} else {
			// sleep so the toast can have time to display
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
			}
			android.os.Process.killProcess(android.os.Process.myPid());  
		}
	}
	
	private void dump(Throwable ex) throws IOException, NameNotFoundException {
		PrintWriter writer = _instance.getWriter();
		if(writer == null)
			return;
		long current = System.currentTimeMillis();
		String time = new SimpleDateFormat("yyyyMMdd.HHmmss").format(new Date(current));
		_writer.println(time);
		PackageManager pm = _context.getPackageManager();
		PackageInfo pi = pm.getPackageInfo(_context.getPackageName(), PackageManager.GET_ACTIVITIES);
		_writer.print("App Version: ");
		_writer.print(pi.versionName);
		_writer.print('_');
		_writer.println(pi.versionCode);  
		_writer.print("OS Version: ");
		_writer.print(Build.VERSION.RELEASE);
		_writer.print("_");
		_writer.println(Build.VERSION.SDK_INT);
		_writer.print("Vendor: ");
		_writer.println(Build.MANUFACTURER);
		_writer.print("Model: ");
		_writer.println(Build.MODEL);
		_writer.print("CPU ABI: ");
		_writer.println();
		ex.printStackTrace(_writer);
		close();
		File file = new File(PATH + "/" + FILE_NAME);
		File file2 = new File(PATH + "/" + FILE_NAME + "." + time + ".txt");
		file.renameTo(file2);
	}
	
}
