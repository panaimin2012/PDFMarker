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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.HashSet;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class LogDog implements UncaughtExceptionHandler {

	public static String 				TAG = "LogDog";

	private static HashSet<String>		_tags = new HashSet<>();
	private static boolean				_adb = true;
	private static boolean				_file = true;

	static {
//		_tags.add(PageActivity.TAG);
	}

	public static LogDog instance() {
		return _instance;
	}

	public static void i(String tag, String message) {
		if (_adb) {
			Log.i(tag, message);
		}
		if (_file) {
			PrintWriter writer = _instance.getWriter();
			if (writer != null && (_tags.isEmpty() || _tags.contains(tag))) {
				_instance._writer.println(Utility.instance().getTimeString() + " i:" + tag + ":" + message);
				_instance._writer.flush();
			}
		}
	}

	public static void e(String tag, String message) {
		if (_adb) {
			Log.e(tag, message);
		}
		if (_file) {
			PrintWriter writer = _instance.getWriter();
			if (writer != null && (_tags.isEmpty() || _tags.contains(tag))) {
				_instance._writer.println(Utility.instance().getTimeString() + " e:" + tag + ":" + message);
				_instance._writer.flush();
			}
		}
	}

	public static void w(String tag, String message) {
		if (_adb) {
			Log.w(tag, message);
		}
		if (_file) {
			PrintWriter writer = _instance.getWriter();
			if (writer != null && (_tags.isEmpty() || _tags.contains(tag))) {
				_instance._writer.println(Utility.instance().getTimeString() + " w:" + tag + ":" + message);
				_instance._writer.flush();
			}
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
			Log.w(TAG, "sdcard unmounted,skip dump exception");
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
			Log.e(TAG, "Failed to open " + PATH + "/" + FILE_NAME);
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
				Toast.makeText(_context, "Sorry for the trouble, dumping uncaught exeption to SD card",
						Toast.LENGTH_LONG).show();
			}
		}.start();
		try {
			dump(ex);
		} catch (Exception e) {
			Log.e(TAG, "Failed to dump because " + e.getMessage());
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
		String time = Utility.instance().getTimeString();
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
		_writer.println();
		ex.printStackTrace(_writer);
		_writer.flush();
		close();
		File file = new File(PATH + "/" + FILE_NAME);
		File file2 = new File(PATH + "/" + FILE_NAME + "." + time + ".txt");
		file.renameTo(file2);
	}

}