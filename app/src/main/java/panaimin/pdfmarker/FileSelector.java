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

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class FileSelector extends ListActivity implements View.OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_file_selector);
		_path = (TextView)findViewById(R.id.cd);
		_cd = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		_adapter = new FileSysAdapter();
		setListAdapter(_adapter);
		_handler.post(_updater);
		findViewById(R.id.ib_search).setOnClickListener(this);
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

	private void searchDirectory(File directory) {
		File[] files = directory.listFiles(new FileFilter() {
			public boolean accept(File file) {
				if (file.isDirectory())
					return false;
				String fname = file.getName().toLowerCase();
				return fname.endsWith(".pdf");
			}
		});
		if (files != null) {
			for (File f : files)
				_adapter.add(new FileSysObject(f, false, false));
		}
		File[] directories = directory.listFiles(new FileFilter() {
			public boolean accept(File file) {
				return file.isDirectory();
			}
		});
		if (directories != null) {
			for (File d : directories)
				searchDirectory(d);
		}
	}

	@Override
	public void onClick(View v) {
		_progressDialog = ProgressDialog.show(this, "Load", "Loading", true);
		new Thread(new Runnable() {
			@Override
			public void run()
			{
				_adapter.clear();
				File parent = _cd.getParentFile();
				if (parent != null)
					_adapter.add(new FileSysObject(parent, true, true));
				searchDirectory(_cd);
				_handler.post(_uiUpdator);
			}
		}).start();
	}
	
	private TextView		_path;
	private File			_cd = null;
	private Handler			_handler = new Handler();
	private Runnable		_updater = new Runnable() {
		@Override
		public void run() {
			_path.setText(_cd.getAbsolutePath());
			File parent = _cd.getParentFile();
			File[] directories = _cd.listFiles(new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory();
				}
			});
			if (directories == null)
				directories = new File[0];
			File[] files = _cd.listFiles(new FileFilter() {
				public boolean accept(File file) {
					if (file.isDirectory())
						return false;
					String fname = file.getName().toLowerCase();
					return fname.endsWith(".pdf");
				}
			});
			if (files == null)
				files = new File[0];
			Arrays.sort(files, new Comparator<File>() {
				public int compare(File arg0, File arg1) {
					return arg0.getName().compareToIgnoreCase(arg1.getName());
				}
			});
			Arrays.sort(directories, new Comparator<File>() {
				public int compare(File arg0, File arg1) {
					return arg0.getName().compareToIgnoreCase(arg1.getName());
				}
			});
			_adapter.clear();
			if (parent != null)
				_adapter.add(new FileSysObject(parent, true, true));
			for (File f : directories)
				_adapter.add(new FileSysObject(f, true, false));
			for (File f : files)
				_adapter.add(new FileSysObject(f, false, false));
			_adapter.notifyDataSetChanged();
		}
	};
	private FileSysAdapter	_adapter = null;
	private ProgressDialog	_progressDialog;
	private Runnable		_uiUpdator = new Runnable() {
		@Override
		public void run() {
			_adapter.notifyDataSetChanged();
			if (_progressDialog != null) {
				_progressDialog.dismiss();
				_progressDialog = null;
			}
		}
	};
	
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
		
		private ArrayList<FileSysObject>	_objects = new ArrayList<>();
		
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
				v = inflater.inflate(R.layout.r_file, vg, false);
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
