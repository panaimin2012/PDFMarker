package panaimin.pdfmarker;

import java.io.File;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.preference.PreferenceManager;

public class PDFMaster {
	
	public static String		TAG = "PDFMarker.Master";
	
	public static final int		PAGE_PREVIOUS = 0;
	public static final int		PAGE_CURRENT = 1;
	public static final int		PAGE_NEXT = 2;

	static public PDFMaster instance() {
		if(_instance == null)
			_instance = new PDFMaster();
		return _instance;
	}
	
	public boolean openPDF(Cursor cursor) {
		if(globals != 0) {
			LogDog.e(TAG, "Previous open file is not properly closed!");
			destroying();
			globals = 0;
		}
		int fileId = cursor.getInt(cursor.getColumnIndex(DB.FILES._ID));
		String pdf = cursor.getString(cursor.getColumnIndex(DB.FILES._PATH));
		LogDog.i(TAG, "Opening " + pdf);
		if(_fileId != fileId || !pdf.equals(_openFile)) {
			// lazy cleanup -- cleanup only if a different file is opened
			discardBitmap(PAGE_PREVIOUS);
			discardBitmap(PAGE_CURRENT);
			discardBitmap(PAGE_NEXT);
			_currentPage = -100;
		}
		_pages = 0;
		File pdfFile = new File(pdf);
		if(!pdfFile.exists())
			return false;
		try {
			globals = openFile(pdf);
			if (globals == 0) {
				throw new Exception(pdf + " failed to open");
			}
			_fileId = fileId;
			_openFile = pdf;
			_pages = countPagesInternal();
			LogDog.i(TAG, "Pages " + _pages);
		}
		catch(Exception e) {
			LogDog.e(TAG, e.getMessage());
			return false;
		}
		_scaleMatrix = null;
		_cutEdgeMatrix = null;
		float[] mv = new float[9];
		mv[0] = cursor.getFloat(cursor.getColumnIndex(DB.FILES._MATRIX0));
		mv[2] = cursor.getFloat(cursor.getColumnIndex(DB.FILES._MATRIX2));
		mv[4] = mv[0];	// same
		mv[5] = cursor.getFloat(cursor.getColumnIndex(DB.FILES._MATRIX5));
		mv[8] = 1.0f;	// constant
		if(mv[0] != 0 || mv[2] != 0 || mv[4] != 0 || mv[5] != 0) {
			_cutEdgeMatrix = new Matrix();
			_cutEdgeMatrix.setValues(mv);
		}
		_fixedMatrix = null;
		LogDog.i(TAG, "Opened " + pdf);
		return true;
	}
	
	public int countPages() {
		return _pages;
	}
	
	// getCachedBitmap simply return the bitmap in cache
	// should be called when bitmap is needed for page turning
	// as it is still possible that the page turning will be canceled
	public Bitmap getCachedBitmap(int offset) {
		LogDog.i(TAG, "getCachedBitmap " + offset);
		if(_bitmaps[offset] == null) {
			if(offset == PAGE_NEXT && _currentPage < _pages - 1)
				_bitmaps[offset] = fetchBitmap(_currentPage + 1);
			else if(offset == PAGE_PREVIOUS && _currentPage > 0)
				_bitmaps[offset] = fetchBitmap(_currentPage - 1);
			else if(offset == PAGE_CURRENT)
				_bitmaps[offset] = fetchBitmap(_currentPage);
		}
		return _bitmaps[offset];
	}
	
	public Matrix getFixedMatrix(int width, int height) {
		LogDog.i(TAG, "getFixedMatrix " + width + "," + height);
		// calculate scale matrix which fit the PDF to screen
		boolean scaleChanged = false;
		boolean keepRatio = PreferenceManager.getDefaultSharedPreferences(PDFMarkerApp.instance()).getBoolean("KEEP_RATIO", false);
		if(_scaleMatrix == null || _width != width || _height != height || _keepRatio != keepRatio) {
			LogDog.i(TAG, "getFixedMatrix scaleChanged");
			Bitmap bmp = getCachedBitmap(PAGE_CURRENT);
			if(bmp != null) {
				float scaleX = (float)width / bmp.getWidth();
				float scaleY = (float)height / bmp.getHeight();
				_scaleMatrix = new Matrix();
				if(keepRatio) {
					if(scaleX > scaleY)
						scaleX = scaleY;
					else if(scaleX < scaleY)
						scaleY = scaleX;
					_scaleMatrix.postTranslate((width - bmp.getWidth()) / 2, (height - bmp.getHeight()) / 2);
					_scaleMatrix.postScale(scaleX, scaleY, width / 2, height / 2);
				} else
					_scaleMatrix.postScale(scaleX, scaleY);
				_width = width;
				_height = height;
			}
			_keepRatio = keepRatio;
			scaleChanged = true;
		}
		boolean fixChanged = false;
		if(scaleChanged || _fixedMatrix == null) {
			LogDog.i(TAG, "getFixedMatrix fixChanged");
			_fixedMatrix = new Matrix(_scaleMatrix);
			fixChanged = true;
		}
		if(_cutEdgeMatrix != null && fixChanged) {
			LogDog.i(TAG, "getFixedMatrix concat cutEdgeMatrix");
			_fixedMatrix.postConcat(_cutEdgeMatrix);
		}
		return _fixedMatrix;
	}
	
	public void cutEdge(Matrix matrix) {
		LogDog.i(TAG, "cutEdge");
		if(_cutEdgeMatrix != null)
			_cutEdgeMatrix.postConcat(matrix);
		else
			_cutEdgeMatrix = new Matrix(matrix);
		if(_fixedMatrix != null)
			_fixedMatrix.postConcat(_cutEdgeMatrix);
		DB.instance().cutEdge(_fileId, _cutEdgeMatrix);
	}
	
	// gotoPage will change the underline current index of page
	// and should be called when actual page is shown
	public Bitmap gotoPage(int page) {
		LogDog.i(TAG, "GOTO " + page + " from " + _currentPage);
		if(page == _currentPage)
			return getCachedBitmap(PAGE_CURRENT);
		else if(page == _currentPage - 2) {
			discardBitmap(PAGE_NEXT);
			discardBitmap(PAGE_CURRENT);
			_bitmaps[PAGE_NEXT] = _bitmaps[PAGE_PREVIOUS];
			_bitmaps[PAGE_CURRENT] = fetchBitmap(page);
			_bitmaps[PAGE_PREVIOUS] = null;
		}
		else if(page == _currentPage - 1) {
			discardBitmap(PAGE_NEXT);
			_bitmaps[PAGE_NEXT] = _bitmaps[PAGE_CURRENT];
			_bitmaps[PAGE_CURRENT] = _bitmaps[PAGE_PREVIOUS];
			_bitmaps[PAGE_PREVIOUS] = null;
		} else if(page == _currentPage + 1) {
			discardBitmap(PAGE_PREVIOUS);
			_bitmaps[PAGE_PREVIOUS] = _bitmaps[PAGE_CURRENT];
			_bitmaps[PAGE_CURRENT] = _bitmaps[PAGE_NEXT];
			_bitmaps[PAGE_NEXT] = null;
		} else if(page == _currentPage + 2) {
			discardBitmap(PAGE_PREVIOUS);
			discardBitmap(PAGE_CURRENT);
			_bitmaps[PAGE_NEXT] = _bitmaps[PAGE_PREVIOUS];
			_bitmaps[PAGE_CURRENT] = fetchBitmap(page);
			_bitmaps[PAGE_PREVIOUS] = null;
		} else {
			discardBitmap(PAGE_PREVIOUS);
			discardBitmap(PAGE_CURRENT);
			discardBitmap(PAGE_NEXT);
			_bitmaps[PAGE_CURRENT] = fetchBitmap(page);
		}
		_currentPage = page;
		new AsyncLoad().execute(null, null, null);
		return getCachedBitmap(PAGE_CURRENT);
	}
	
	private void discardBitmap(int offset) {
		if(_bitmaps[offset] != null) {
			_bitmaps[offset].recycle();
			_bitmaps[offset] = null;
		}
	}
	
	private synchronized Bitmap fetchBitmap(int page) {
		LogDog.i(TAG, "Fetch " + page);
		try {
			if(globals == 0)
				throw new Exception("No open PDF file");
			gotoPageInternal(page);
			int w = (int)getPageWidth();
			int h = (int)getPageHeight();
			Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
			if(!drawPage(bmp, w, h, 0, 0, w, h))
				throw new Exception("jniDraw failed");
			LogDog.i(TAG, "Fetch done");
			return bmp;
		}
		catch(Exception e) {
			LogDog.e(TAG, e.getMessage());
		}
		return null;
	}
	
	public void closePDF() {
		LogDog.i(TAG, "close " + _openFile);
		destroying();
		globals = 0;
		// _openfile is not cleaned and will be used if same file is opened again
	}

	// private

	static private PDFMaster	_instance = null;
	private int					_fileId = -1;
	private String				_openFile;
	private int					_width = -1;
	private int					_height = -1;
	private boolean				_keepRatio = false;
	private Matrix				_scaleMatrix;	// scale original pdf to the screen
	private Matrix				_cutEdgeMatrix;	// user defined matrix to cut the paper edge
	private Matrix				_fixedMatrix;	// product of _scaleMatrix and _cutEdgeMatrix;
	private int					_pages;
	private Bitmap[]			_bitmaps = new Bitmap[3];
	private int					_currentPage = -100;	// remember the last page called with getBitmap
	
	private long			globals = 0;
	private native long		openFile(String filename);
	private native String	fileFormatInternal();
	private native int		countPagesInternal();
	private native void		gotoPageInternal(int page);
	private native float	getPageWidth();
	private native float	getPageHeight();
	private native boolean	drawPage(Bitmap bmp, int w, int h, int x, int y, int pw, int ph);
	private native void		destroying();

	static {
		System.loadLibrary("mupdf");
	}

	private PDFMaster() {}

	private synchronized void draw(Bitmap bmp, int w, int h, int x, int y, int pw, int ph) {
		LogDog.i(TAG, "draw");
		drawPage(bmp, w, h, x, y, pw, ph);
	}
	
	private class AsyncLoad extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			LogDog.i(TAG, "AsyncLoad doInBackGround start");
			if(_bitmaps[PAGE_NEXT] == null && _currentPage < _pages - 1)
				_bitmaps[PAGE_NEXT] = fetchBitmap(_currentPage + 1);
			if(_bitmaps[PAGE_PREVIOUS] == null && _currentPage > 0)
				_bitmaps[PAGE_PREVIOUS] = fetchBitmap(_currentPage - 1);
			LogDog.i(TAG, "AsyncLoad doInBackGround end");
			return null;
		}
	}
}
