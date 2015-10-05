package panaimin.pdfmarker;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class FileSelector extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_file_selector);
		_path = (TextView)findViewById(R.id.cd);
		_cd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		_adapter = new FileSysAdapter();
		setListAdapter(_adapter);
		_handler = new Handler();
		_updater = new FileSysUpdater();
		_handler.post(_updater);
	}
	
	@Override
	public void onStop() {
		super.onStop();
		// return to the Main activity
		Intent intent = new Intent(this, RecentlyUsedActivity.class);
		setResult(RESULT_CANCELED, intent);
		finish();
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		FileSysObject obj = (FileSysObject)_adapter.getItem(position);
		if(obj._isDirectory) {
			_cd = obj._file;
			_handler.post(_updater);
			return;
		}
		// return to the Main activity
		Intent intent = new Intent(this, RecentlyUsedActivity.class);
		intent.putExtra("NAME", obj._file.getName());
		intent.putExtra("PATH", obj._file.getPath());
		setResult(RESULT_OK, intent);
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.file_selector, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private TextView		_path;
	private File			_cd = null;
	private File			_parent;
	private File[]			_directories;
	private File[]			_files;
	private Handler			_handler;
	private FileSysUpdater	_updater = null;
	private FileSysAdapter	_adapter = null;
	
	// Runnable class that updates the file/directory list
	private class FileSysUpdater implements Runnable {
		@Override
		public void run() {
			_path.setText(_cd.getAbsolutePath());
			_parent = _cd.getParentFile();
			_directories = _cd.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory();
				}
			});
			if (_directories == null)
				_directories = new File[0];
			_files = _cd.listFiles(new FileFilter() {
				public boolean accept(File file) {
					if (file.isDirectory())
						return false;
					String fname = file.getName().toLowerCase();
					return fname.endsWith(".pdf");
				}
			});
			if (_files == null)
				_files = new File[0];
			Arrays.sort(_files, new Comparator<File>() {
				public int compare(File arg0, File arg1) {
					return arg0.getName().compareToIgnoreCase(arg1.getName());
				}
			});
			Arrays.sort(_directories, new Comparator<File>() {
				public int compare(File arg0, File arg1) {
					return arg0.getName().compareToIgnoreCase(arg1.getName());
				}
			});
			_adapter.clear();
			if (_parent != null)
				_adapter.add(new FileSysObject(_parent, true, true));
			for (File f : _directories)
				_adapter.add(new FileSysObject(f, true, false));
			for (File f : _files)
				_adapter.add(new FileSysObject(f, false, false));
			_adapter.notifyDataSetChanged();
		}
	}
	
	// FileSysObject
	private class FileSysObject {
		public File		_file;
		public boolean	_isDirectory;
		public boolean	_isParent;
		public FileSysObject(File file, boolean isDirectory, boolean isParent) {
			_file = file;
			_isDirectory = isDirectory;
			_isParent = isParent;
		}
	}
	
	// Adapter class to show file system in the list
	private class FileSysAdapter extends BaseAdapter {
		
		private ArrayList<FileSysObject>	_objects = new ArrayList<FileSysObject>();
		
		public void clear() {
			_objects.clear();
		}
		
		public void add(FileSysObject obj) {
			_objects.add(obj);
		}
		
		@Override
		public int getCount() {
			return _objects.size();
		}
		
		@Override
		public Object getItem(int i) {
			return _objects.get(i);
		}
		
		@Override
		public long getItemId(int i) {
			return i;
		}
		
		@Override
		public View getView(int i, View v, ViewGroup vg) {
			if(v == null) {
				LayoutInflater inflater =
					(LayoutInflater)PDFMarkerApp.instance().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.layout_file_item, vg, false);
			}
			FileSysObject obj = _objects.get(i);
			ImageView iv = (ImageView)v.findViewById(R.id.indicator);
			iv.setImageResource(obj._isDirectory ? R.drawable.ic_folder : R.drawable.ic_pdf);
			TextView name = (TextView)v.findViewById(R.id.fileName);
			name.setText(obj._isParent ? ".." : obj._file.getName());
			TextView path = (TextView)v.findViewById(R.id.directory);
			path.setText(obj._file.getPath());
			return v;
		}
		
	}
}
