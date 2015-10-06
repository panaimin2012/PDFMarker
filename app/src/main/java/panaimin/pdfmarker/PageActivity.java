package panaimin.pdfmarker;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;

public class PageActivity extends Activity implements OnTouchListener {
	
	public static String TAG = "PAGE";
	
	public boolean	_markMode = false;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_page);
		_forePage = (PageView)findViewById(R.id.container);
		// try to show the passed in page
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			_fileId = extras.getInt("FILE_ID");
		}
		_scaleListener = new ScaleListener();
		_scaleDetector = new ScaleGestureDetector(this, _scaleListener);
		_forePage.setOnTouchListener(this);
		// reset the stationary when opening a page
		Stationary.setCurrentStationary(Stationary.PENCIL * Stationary.M + Stationary.P_HB);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		Cursor cursor = DB.instance().getFileInfo(_fileId);
		_fileName = cursor.getString(cursor.getColumnIndex(DB.FILES._FILE));
		_pageId = cursor.getInt(cursor.getColumnIndex(DB.FILES._PAGE));
		PDFMaster.instance().openPDF(cursor);
		cursor.close();
		setTitle(_fileName);
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
			if(_matrix != null) {
				PDFMaster.instance().cutEdge(_matrix);
				_matrix = null;
				_scale = 1.0f;
				_forePage.setDrawingMatrix(_matrix);
				_forePage.refresh();
				PDFMarkerApp.instance().showToast(getResources().getString(R.string.msg_cut));
			}
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

	public void turnPage(int direction) {
		turnToPage(_pageId + direction);
	}
	
	public boolean validTurn(int direction) {
		if(_pageId + direction < 0) {
			PDFMarkerApp.instance().showToast(getResources().getString(R.string.msg_firstpage));
			return false;
		} else if(_pageId + direction >= PDFMaster.instance().countPages()) {
			PDFMarkerApp.instance().showToast(getResources().getString(R.string.msg_lastpage));
			return false;
		}
		return true;
	}

	public void turnToPage(int pageNumber) {
		_pageId = pageNumber;
		_forePage.refresh();
		DB.instance().setLastPage(_fileId, _pageId);
		PDFMarkerApp.instance().showToast("Page " + (_pageId + 1));
	}

	public int getFileId() { return _fileId; }
	public int getPageId() { return _pageId; }
	
	// private
	
	private int						_fileId;
	private int						_pageId;
	private String					_fileName;
	private PageView				_forePage;
	private StationaryDialog		_stationaryDialog = null;
	
	private ScaleGestureDetector	_scaleDetector;
	private ScaleListener			_scaleListener;
	// I need to remember _scale so I can control it to be in range 1 to 4
	private static final float		MAX_SCALE = 4.0f;
	private float					_scale = 1.0f;
	private Matrix					_matrix = null;
	private float[]					_testPoints = null;
	private float[]					_testResults = new float[] {0f, 0f, 0f, 0f};
	private float					_downX;
	private float					_downY;
	private float					_focusX;
	private float					_focusY;
	private boolean					_moving = false;
	
	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if(_markMode)
			return false;
		_scaleDetector.onTouchEvent(event);
		// scroll happens only when zoomed in (scale > 1)
		if(_scale > 1.0f) {
			// don't scroll, else image jumps
			if(!_scaleListener._scaling) {
				float x = event.getX();
				float y = event.getY();
				switch(event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN :
					_downX = x;
					_downY = y;
					_moving = true;
					if(_testPoints == null)
						_testPoints = new float[] {0f, 0f, _forePage.getWidth(), _forePage.getHeight()};
					break;
				case MotionEvent.ACTION_MOVE :
					// the _moving flag:
					// when scaling finishes, its MOVE event still comes which should not drag the page
					// this caused a sudden drag at the end of the scaling
					if(_moving) {
						Matrix newMatrix = new Matrix(_matrix);
						newMatrix.postTranslate(x - _downX, y - _downY);
						_downX = x;
						_downY = y;
						newMatrix.mapPoints(_testResults, _testPoints);
						if(_testResults[0] > _testPoints[0])
							newMatrix.postTranslate(-_testResults[0], 0);
						if(_testResults[1] > _testPoints[1])
							newMatrix.postTranslate(0, -_testResults[1]);
						if(_testResults[2] < _testPoints[2])
							newMatrix.postTranslate(_testPoints[2] - _testResults[2], 0);
						if(_testResults[3] < _testPoints[3])
							newMatrix.postTranslate(0, _testPoints[3] - _testResults[3]);
						_matrix = newMatrix;
						_forePage.setDrawingMatrix(_matrix);
					}
					break;
				case MotionEvent.ACTION_UP :
					// fall through
				case MotionEvent.ACTION_POINTER_DOWN :
					_moving = false;
					break;
				}
			}
			return true;
		}
		// if it is neither mark mode nor zoomed in, let MyPage handler page turning
		return false;
	}
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		
		public boolean		_scaling = false;
		
		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			if(_matrix == null)
				_matrix = new Matrix();
			if(_testPoints == null)
				_testPoints = new float[] {0f, 0f, _forePage.getWidth(), _forePage.getHeight()};
			// focus changes in one scale action and causes image jump
			// so we decide it at the beginning to make it smooth
			_focusX = detector.getFocusX();
			_focusY = detector.getFocusY();
			_scaling = true;
			return true;
		}
		
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			float newScale = detector.getScaleFactor();
			// don't scale too big or too small
			if(_scale * newScale < 1f) {
				_matrix = null;
				_scale = 1f;
				_forePage.setDrawingMatrix(_matrix);
				return true;
			}
			if(_scale * newScale > MAX_SCALE) {
				return true;
			}
			// copy current Matrix to new and apply the change to test
			Matrix newMatrix = new Matrix(_matrix);
			newMatrix.postTranslate(-_focusX, -_focusY);
			newMatrix.postScale(newScale, newScale);
			newMatrix.postTranslate(_focusX, _focusY);
			newMatrix.mapPoints(_testResults, _testPoints);
			if(_testResults[0] > _testPoints[0])
				newMatrix.postTranslate(-_testResults[0], 0);
			if(_testResults[1] > _testPoints[1])
				newMatrix.postTranslate(0, -_testResults[1]);
			if(_testResults[2] < _testPoints[2])
				newMatrix.postTranslate(_testPoints[2] - _testResults[2], 0);
			if(_testResults[3] < _testPoints[3])
				newMatrix.postTranslate(0, _testPoints[3] - _testResults[3]);
			_scale *= newScale;
			_matrix = newMatrix;
			_forePage.setDrawingMatrix(_matrix);
			return true;
		}
		
		@Override
		public void onScaleEnd(android.view.ScaleGestureDetector detector) {
			_scaling = false;
		}
	}
	
}
