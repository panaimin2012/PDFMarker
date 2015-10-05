package panaimin.pdfmarker;

import java.io.File;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

public class RecentlyUsedActivity extends ListActivity implements OnItemLongClickListener {
	
	public static String TAG = "RECENT";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_main);
		Cursor cursor = DB.instance().getFiles();
		String[] cols = new String[] { DB.FILES._FILE, DB.FILES._PATH };
		int[] ids = new int[] { R.id.fileName, R.id.directory };
		_adapter = new SimpleCursorAdapter(this, R.layout.layout_file_item, cursor, cols, ids, 0);
		setListAdapter(_adapter);
		getListView().setOnItemLongClickListener(this);
		_stopped = false;
	}
	
	@Override
	public void onStop() {
		super.onStop();
		_stopped = true;
	}
	
	@Override
	public void onResume() {
		super.onResume();
		if(_stopped) {
			Cursor cursor = DB.instance().getFiles();
			_adapter.changeCursor(cursor);
			_adapter.notifyDataSetChanged();
			_stopped = false;
		}
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
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		openPDF((int)id);
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
	
	private CursorAdapter	_adapter;
	private boolean			_stopped = false;
	
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
					SVGRecorder.reset();
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
