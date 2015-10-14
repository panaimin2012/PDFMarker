package panaimin.pdfmarker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

public class PageActivity extends Activity implements OnTouchListener {
	
	public static String TAG = "PAGE";
	
	boolean	_markMode = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_page);
		_svgView = (SVGView)findViewById(R.id.container);
		// try to show the passed in page
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			_fileId = extras.getInt("FILE_ID");
		}
		_svgView.setOnTouchListener(this);
		// reset the stationary when opening a page
		Stationary.setCurrentStationary(Stationary.PENCIL * Stationary.M + Stationary.P_HB);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Cursor cursor = DB.instance().getFileInfo(_fileId);
		String fileName = cursor.getString(cursor.getColumnIndex(DB.FILES._FILE));
		_pageId = cursor.getInt(cursor.getColumnIndex(DB.FILES._PAGE));
		PDFMaster.instance().openPDF(cursor);
		cursor.close();
		setTitle(fileName);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		SVGRecorder.getInstance(_fileId, _pageId).saveSVG();
		PDFMaster.instance().closePDF();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.page, menu);
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
		else if(id == R.id.action_mode) {
			_markMode = !_markMode;
			if(_markMode) {
				if(_stationaryDialog == null) {
					_stationaryDialog = new StationaryDialog(this, item);
					_stationaryDialog.setOwnerActivity(this);
				}
				_stationaryDialog.display();
			}
			else
				item.setIcon(R.drawable.ic_hand);
			return true;
		}
		else if(id == R.id.action_cut) {
			_svgView.cutEdge();
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

	public int getFileId() { return _fileId; }
	public int getPageId() { return _pageId; }

	public void turnToPage(int pageNumber) {
		_pageId = pageNumber;
		_svgView.refresh();
		DB.instance().setLastPage(_fileId, _pageId);
		PDFMarkerApp.instance().showToast("Page " + (_pageId + 1));
	}

	// private
	
	private int						_fileId;
	private int						_pageId;
	private SVGView					_svgView;
	private StationaryDialog		_stationaryDialog = null;
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return false;
	}
	
}
