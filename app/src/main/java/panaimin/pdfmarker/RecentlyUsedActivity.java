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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class RecentlyUsedActivity extends ListActivity implements OnItemLongClickListener {
	
	static String TAG = "PDFMarker.Recent";

	private CursorAdapter	_adapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_main);
		Cursor cursor = DB.instance().getFiles();
		String[] cols = new String[] { DB.FILES._FILE, DB.FILES._PATH };
		int[] ids = new int[] { R.id.fileName, R.id.directory };
		_adapter = new SimpleCursorAdapter(this, R.layout.r_file, cursor, cols, ids, 0);
		setListAdapter(_adapter);
		getListView().setOnItemLongClickListener(this);
		// show license dialog for the first time
		if (Utility.instance().getPref(PDFMarkerApp.PREF_SHOW_LICENSE, true)) {
			LicenseDialog dlg = new LicenseDialog(this);
			dlg.show();
		}
		Intent intent = getIntent();
		String action = intent.getAction();
		if (action.equals(Intent.ACTION_VIEW) || action.equals(Intent.ACTION_OPEN_DOCUMENT)) {
			String fileName;
			String filePath;
			String scheme = intent.getScheme();
			ContentResolver resolver = getContentResolver();
			if (scheme.compareTo(ContentResolver.SCHEME_CONTENT) == 0
					|| scheme.compareTo("http") == 0
					|| scheme.compareTo("https") == 0) {
				Uri uri = intent.getData();
				fileName = getContentName(resolver, uri);
				try {
					InputStream input = resolver.openInputStream(uri);
					filePath = PDFMarkerApp.instance().getFilesDir() + "/" + fileName;
					InputStreamToFile(input, filePath);
					// save the file to database first
					int fileId = DB.instance().insert(fileName, filePath);
					// open the file
					openPDF(fileId);
				}
				catch(Exception e) {
					LogDog.e(TAG, "Failed to view because " + e.getMessage());
				}
			} else if (scheme.compareTo(ContentResolver.SCHEME_FILE) == 0) {
				Uri uri = intent.getData();
				fileName = uri.getLastPathSegment();
				int fileId = DB.instance().insert(fileName, uri.getPath());
				openPDF(fileId);
			}
		}
	}

	private String getContentName(ContentResolver resolver, Uri uri){
		Cursor cursor = resolver.query(uri, null, null, null, null);
		cursor.moveToFirst();
		int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
		String ret = null;
		if (nameIndex >= 0)
			ret = cursor.getString(nameIndex);
		cursor.close();
		return ret;
	}

	private void InputStreamToFile(InputStream in, String file) throws IOException {
		OutputStream out = new FileOutputStream(new File(file));
		int size = 0;
		byte[] buffer = new byte[1024];
		while ((size = in.read(buffer)) != -1) {
			out.write(buffer, 0, size);
		}
		out.close();
	}

	@Override
	public void onStart() {
		super.onStart();
		Cursor cursor = DB.instance().getFiles();
		_adapter.changeCursor(cursor);
		_adapter.notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent i = new Intent().setClass(this, SettingsActivity.class);
			startActivity(i);
			return true;
		}
		else if(id == R.id.action_file) {
			Intent i = new Intent().setClass(this, FileSelector.class);
			startActivityForResult(i, id);
			return true;
		}
		else if(id == R.id.action_about) {
			Intent i = new Intent().setClass(this, DocActivity.class);
			i.putExtra("DOC", "about");
			startActivity(i);
			return true;
		}
		else if(id == R.id.action_manual) {
			Intent i = new Intent().setClass(this, DocActivity.class);
			i.putExtra("DOC", "manual");
			startActivity(i);
			return true;
		}
		else if (id == R.id.action_license) {
			LicenseDialog dlg = new LicenseDialog(this);
			dlg.show();
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		openPDF((int) id);
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == R.id.action_file && resultCode == RESULT_OK) {
			String name = (String)data.getExtras().get("NAME");
			String path = (String)data.getExtras().get("PATH");
			// save the file to database first
			int fileId = DB.instance().insert(name, path);
			// open the file
			openPDF(fileId);
		}
	}

	private void openPDF(int fileId) {
		Cursor cursor = DB.instance().getFileInfo(fileId);
		if(cursor.getCount() > 0) {
			DB.instance().onFileOpen(fileId);
			Intent intent = new Intent(this, PageActivity.class);
			intent.putExtra("FILE_ID", fileId);
			startActivity(intent);
		}
	}
	
	private int _deletingFileId = -1;
	
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
		DB.instance().markDeleted((int)id, true);
		Cursor cursor = DB.instance().getFiles();
		_adapter.changeCursor(cursor);
		_adapter.notifyDataSetChanged();
		// Ask user if want to delete all marks
		_deletingFileId = (int)id;
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(R.string.delete_marks).setCancelable(false).setPositiveButton("YES",
			new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					File directory = getFilesDir();
					File[] files = directory.listFiles();
					String prefix = "" + _deletingFileId + ".";
					String suffix = ".svg";
					for(int i = files.length - 1; i >= 0; --i) {
						File f = files[i];
						if(f.getName().startsWith(prefix) && f.getName().endsWith(suffix)) {
							LogDog.i(TAG, "DELETE " + f.getName());
							f.delete();
						}
					}
					DB.instance().cutEdge(_deletingFileId, null);
					DB.instance().setLastPage(_deletingFileId, 0);
					dialog.cancel();
				}
			}
		).setNegativeButton("NO",
			new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.cancel();
				}
			}
		);
		AlertDialog dialog = builder.create();
		dialog.show();
		return true;
	}

}
