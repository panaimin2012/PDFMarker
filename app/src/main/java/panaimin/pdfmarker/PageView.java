package panaimin.pdfmarker;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class PageView extends SurfaceView implements SurfaceHolder.Callback, OnSharedPreferenceChangeListener  {
	
	public static final String	TAG = "FORE";
	private static final int	EDGE_WIDTH = 50;
	private static final int	TURN_TOLERANCE = 100; // don't turn page if pointer x change within tolerance

	public PageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		_turnThread = new TurnThread(this);
		getHolder().addCallback(this);
		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PDFMarkerApp.instance());
		sp.registerOnSharedPreferenceChangeListener(this);
	}
	
	// back page related
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(key.equals("KEEP_RATIO")) {
			_fixedMatrix = PDFMaster.instance().getFixedMatrix(_width, _height);
			setDrawingMatrix(_dynamicMatrix);
		}
	}
	
	private Matrix					_fixedMatrix = null;
	private Matrix					_matrix = null;			// composite of static and dynamic
	private Bitmap					_backPage = null;
	
	// marking related
	
	private SVGRecorder				_svgRecorder;
	private SVGRecorder.SVGPath		_currentPath = null;
	private int						_width = -1;
	private int						_height = -1;
	private Matrix					_dynamicMatrix = null;
	private float[]					_dynamicMatrixValues = null;
	
	public void refresh() {
		PageActivity activity = (PageActivity)getContext();
		int fileId = activity.getFileId();
		int pageId = activity.getPageId();
		_svgRecorder = SVGRecorder.getInstance(fileId, pageId);
		_backPage = PDFMaster.instance().gotoPage(pageId);
		if(_backPage == null)
			return;
		_fixedMatrix = PDFMaster.instance().getFixedMatrix(_width, _height);
		_matrix = new Matrix(_fixedMatrix);
		if(_dynamicMatrix != null) {
			_matrix.postConcat(_dynamicMatrix);
		}
		draw();
	}
	
	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		_width = w;
		_height = h;
		SVGRecorder.setSize(_width, _height);;
		refresh();
	}
	
	public void setDrawingMatrix(Matrix matrix) {
		_dynamicMatrix = matrix;
		_dynamicMatrixValues = null;
		if(_fixedMatrix == null)
			return;
		_matrix = new Matrix(_fixedMatrix);
		if(_dynamicMatrix != null)
			_matrix.postConcat(_dynamicMatrix);
		draw();
	}
	
	private float getUnscaledX(float x) {
		if(_dynamicMatrix != null) {
			if(_dynamicMatrixValues == null) {
				_dynamicMatrixValues = new float[9];
				_dynamicMatrix.getValues(_dynamicMatrixValues);
			}
			return (x - _dynamicMatrixValues[2]) / _dynamicMatrixValues[0];
		}
		return x;
	}

	private float getUnscaledY(float y) {
		if(_dynamicMatrix != null)
			// getUnsclaedX is called before getUnscaledY so we can assume _matrixValues are set
			return (y - _dynamicMatrixValues[5]) / _dynamicMatrixValues[4];
		return y;
	}
	
	public void turnFinished() {
		if(_turnDirection == _turnThread.autoDirection()) {
			// if not equal, it means turn is cancelled
			_handler.post(_turnFinishRunnable);
		} else
			draw();
	}
	
	public void draw() {
		if(!_goodSurface)
			return;
		Canvas canvas = getHolder().lockCanvas();
		// draw back page first
		boolean keepRatio = PreferenceManager.getDefaultSharedPreferences(PDFMarkerApp.instance())
			.getBoolean("KEEP_RATIO", false);
		if(keepRatio)
			canvas.drawRGB(0, 0, 0); // so that edge are black
		canvas.drawBitmap(_backPage, _matrix, null);
		// draw svg
		if(_dynamicMatrix != null) {
			canvas.save();
			canvas.concat(_dynamicMatrix);
		}
		canvas.drawBitmap(_svgRecorder.getBitmap(), 0, 0, null);
		if(_currentPath != null)
			canvas.drawPath(_currentPath, Stationary.getCurrentPaint());
		if(_dynamicMatrix != null) {
			canvas.restore();
		}
		getHolder().unlockCanvasAndPost(canvas);
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// when event arrives here
		// 1. mark mode (writing
		// 2. scale = 1, need to check the start to decide turn page
		PageActivity activity = (PageActivity)getContext();
		int action = event.getAction() & MotionEvent.ACTION_MASK;
		if(activity._markMode) {
			// writing
			switch(action) {
			case MotionEvent.ACTION_DOWN :
				_currentPath = _svgRecorder.new SVGPath(Stationary.getCurrentStationary());
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				draw();
				break;
			case MotionEvent.ACTION_UP :
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				_svgRecorder.addPath(_currentPath);
				_currentPath = null;
				draw();
				break;
			case MotionEvent.ACTION_MOVE :
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				draw();
				break;
			}
		} else {
			// page turn
			SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(PDFMarkerApp.instance());
			boolean fastMode = sp.getBoolean("FAST_PAGE_TURN", false) || Build.VERSION.SDK_INT < 16;
			float x = event.getX();
			switch(action) {
			case MotionEvent.ACTION_DOWN :
				if(_turnThread.status() != TurnThread.STATUS_IDLING)
					return true;
				if(x < EDGE_WIDTH && activity.validTurn(TurnThread.DIR_BACKWARD)) {
					if(fastMode) {
						activity.turnPage(TurnThread.DIR_BACKWARD);
						return true;
					} else {
						initPageTurn(TurnThread.DIR_BACKWARD, TurnThread.DIR_NONE);
					}
				} else if(x > getWidth() - EDGE_WIDTH && activity.validTurn(TurnThread.DIR_FORWARD)) {
					if(fastMode) {
						activity.turnPage(TurnThread.DIR_FORWARD);
						return true;
					} else {
						initPageTurn(TurnThread.DIR_FORWARD, TurnThread.DIR_NONE);
					}
				} else
					_downX = x;
				break;
			case MotionEvent.ACTION_MOVE :
				if(_turnThread.status() != TurnThread.STATUS_IDLING) {
					if(_turnDirection == TurnThread.DIR_FORWARD)
						_turnThread.setTargetLeftMost(x);
					else if(_turnDirection == TurnThread.DIR_BACKWARD)
						_turnThread.setTargetRightMost(x);
				}
				break;
			case MotionEvent.ACTION_UP :
				// cancel or finish
				if(_turnThread.status() != TurnThread.STATUS_IDLING) {
					_turnThread.setAutoDirection(x >= _width / 2 ? TurnThread.DIR_BACKWARD : TurnThread.DIR_FORWARD);
				} else if(_downX >= 0 && Math.abs(x - _downX) > TURN_TOLERANCE) {
					int direction = _downX < x ? TurnThread.DIR_BACKWARD : TurnThread.DIR_FORWARD;
					if(activity.validTurn(direction)) {
						if(fastMode) {
							activity.turnPage(direction);
						} else {
							initPageTurn(direction, direction);
						}
					}
				} else if(_downX >= 0) {
					if(_gotoDialog == null)
						_gotoDialog = new GotoDialog(activity);
					_gotoDialog.setPage(activity.getPageId() + 1);
					_gotoDialog.show();
				}
				// pass through
			case MotionEvent.ACTION_POINTER_DOWN :
				if(_turnThread.status() == TurnThread.STATUS_IDLING)
					_downX = -100;
				break;
			}
		}
		// No more handles, so return true to stop 
		return true;
	}
	
	// page turning related
	
	private GotoDialog				_gotoDialog = null;
	private TurnThread				_turnThread = null;
	private int						_turnDirection = 0;
	private float					_downX = -100;
	private Handler					_handler = new Handler();
	private Runnable				_turnFinishRunnable = new Runnable() {
		@Override
		public void run() {
			((PageActivity)getContext()).turnPage(_turnDirection);
		}
	};
	
	private void initPageTurn(int direction, int autoDirection) {
		_turnDirection = direction;
		Bitmap leftOriginal = PDFMaster.instance().getCachedBitmap(
			_turnDirection == TurnThread.DIR_BACKWARD ? PDFMaster.PAGE_PREVIOUS : PDFMaster.PAGE_CURRENT);
		Bitmap left = Bitmap.createBitmap(_width, _height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(left);
		Matrix m = PDFMaster.instance().getFixedMatrix(_width, _height);
		canvas.drawBitmap(leftOriginal, m, null);
		canvas.drawBitmap(SVGRecorder.getCachedBitmap(
			_turnDirection == TurnThread.DIR_BACKWARD ? SVGRecorder.PAGE_PREVIOUS : SVGRecorder.PAGE_CURRENT), 0,  0, null);
		Bitmap rightOriginal = PDFMaster.instance().getCachedBitmap(
			_turnDirection == TurnThread.DIR_BACKWARD ? PDFMaster.PAGE_CURRENT : PDFMaster.PAGE_NEXT);
		Bitmap right = Bitmap.createBitmap(_width, _height, Bitmap.Config.ARGB_8888);
		Canvas canvas2 = new Canvas(right);
		canvas2.drawBitmap(rightOriginal, m, null);
		canvas2.drawBitmap(SVGRecorder.getCachedBitmap(
			_turnDirection == TurnThread.DIR_BACKWARD ? SVGRecorder.PAGE_CURRENT : SVGRecorder.PAGE_NEXT), 0, 0, null);
		_turnThread.setBitmaps(left, right);
		_turnThread.kickOff(_turnDirection, autoDirection);
	}
	
	private boolean _goodSurface = false;
	
	public boolean goodSurface() { return _goodSurface; }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		_goodSurface = true;
		draw();
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		_goodSurface = false;
	}
	
}
