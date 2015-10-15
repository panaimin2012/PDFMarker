package panaimin.pdfmarker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class SVGView extends View {

	static final String TAG = "PDFMarker.SVGView";

	private int					_width = 0;
	private int					_height = 0;
	private SVGRecorder			_svgRecorder;
	private Bitmap				_pdf;
	private SVGRecorder.SVGPath _currentPath;
	private PageActivity		_activity;
	private GotoDialog			_gotoDialog = null;

	public SVGView(Context context, AttributeSet attrs) {
		super(context, attrs);
		_activity = (PageActivity)context;
		_scaleListener = new ScaleListener();
		_scaleDetector = new ScaleGestureDetector(PDFMarkerApp.instance(), _scaleListener);
	}

	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		_width = w;
		_height = h;
		SVGRecorder.setSize(_width, _height);
		refresh();
	}

	public void refresh() {
		PageActivity activity = (PageActivity)getContext();
		int fileId = activity.getFileId();
		int pageId = activity.getPageId();
		_svgRecorder = SVGRecorder.getInstance(fileId, pageId);
		_pdf = PDFMaster.instance().gotoPage(pageId);
		_fixedMatrix = PDFMaster.instance().getFixedMatrix(_width, _height);
		setDynamicMatrix(_dynamicMatrix);
	}

	void setDynamicMatrix(Matrix matrix) {
		_dynamicMatrix = matrix;
		_dynamicMatrixValues = null;
		if(_fixedMatrix == null)
			return;
		_displayMatrix = new Matrix(_fixedMatrix);
		if(_dynamicMatrix != null)
			_displayMatrix.postConcat(_dynamicMatrix);
		Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
		Bitmap bmp = Bitmap.createBitmap(_width, _height, conf); // this creates a MUTABLE bitmap
		Canvas canvas = new Canvas(bmp);
		canvas.drawBitmap(_pdf, _displayMatrix, null);
		BitmapDrawable bg = new BitmapDrawable(getResources(), bmp);
		setBackground(bg);
		invalidate();
	}

	void cutEdge() {
		if(_dynamicMatrix != null) {
			PDFMaster.instance().cutEdge(_dynamicMatrix);
			_scale = 1.0f;
			setDynamicMatrix(null);
			refresh();
			PDFMarkerApp.instance().showToast(getResources().getString(R.string.msg_cut));
		}
	}

	@Override
	public void onDraw(Canvas canvas) {
		// draw svg
		if(_dynamicMatrix != null)
			canvas.concat(_dynamicMatrix);
		canvas.drawBitmap(_svgRecorder.getBitmap(), 0, 0, null);
		if(_currentPath != null) {
			canvas.drawPath(_currentPath, Stationary.getCurrentPaint());
		}
		if(_dynamicMatrix != null)
			canvas.restore();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		int action = event.getAction() & MotionEvent.ACTION_MASK;
		if (_activity._markMode) {
			switch(action) {
			case MotionEvent.ACTION_DOWN :
				_currentPath = _svgRecorder.new SVGPath(Stationary.getCurrentStationary());
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				break;
			case MotionEvent.ACTION_UP :
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				_svgRecorder.addPath(_currentPath);
				_currentPath = null;
				break;
			case MotionEvent.ACTION_MOVE :
				_currentPath.addPoint(getUnscaledX(event.getX()), getUnscaledY(event.getY()));
				break;
			}
		} else {
			_scaleDetector.onTouchEvent(event);
			// scroll happens only when zoomed in (scale > 1)
			if(_scale > 1.0f) {
				// don't scroll, else image jumps
				if(!_scaleListener._scaling) {
					float x = event.getX();
					float y = event.getY();
					switch(action) {
					case MotionEvent.ACTION_DOWN :
						_downX = x;
						_downY = y;
						_moving = true;
						if(_testPoints == null)
							_testPoints = new float[] {0f, 0f, _width, _height};
						break;
					case MotionEvent.ACTION_MOVE :
						// the _moving flag:
						// when scaling finishes, its MOVE event still comes which should not drag the page
						// this caused a sudden drag at the end of the scaling
						if(_moving) {
							Matrix newMatrix = new Matrix(_dynamicMatrix);
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
							setDynamicMatrix(newMatrix);
						}
						break;
					case MotionEvent.ACTION_UP :
						// fall through
					case MotionEvent.ACTION_POINTER_DOWN :
						_moving = false;
						break;
					}
				}
			}
			else if (action == MotionEvent.ACTION_UP){
				if (_gotoDialog == null)
					_gotoDialog = new GotoDialog(_activity);
				_gotoDialog.setPage(_activity.getPageId() + 1);
				_gotoDialog.show();
			}
		}
		invalidate();
		return true;
	}

	// scale related

	private ScaleGestureDetector	_scaleDetector;
	private ScaleListener			_scaleListener;
	private static final float		MAX_SCALE = 4.0f;
	private float					_scale = 1.0f;
	private Matrix					_fixedMatrix = null;
	private Matrix					_dynamicMatrix = null;
	private Matrix					_displayMatrix = null; // product of fixedMatrix * dynamicMatrix
	private float[]					_dynamicMatrixValues;
	private float[]					_testPoints = null; // test corner points so don't move out screen
	private float[]					_testResults = new float[] {0f, 0f, 0f, 0f};
	private float					_downX;
	private float					_downY;
	private float					_focusX;
	private float					_focusY;
	private boolean					_moving = false;

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

	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

		boolean _scaling = false;

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			if(_dynamicMatrix == null)
				_dynamicMatrix = new Matrix();
			if(_testPoints == null)
				_testPoints = new float[] {0f, 0f, _width, _height};
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
				_scale = 1f;
				setDynamicMatrix(null);
				return true;
			}
			if(_scale * newScale > MAX_SCALE) {
				return true;
			}
			// copy current Matrix to new and apply the change to test
			Matrix newMatrix = new Matrix(_dynamicMatrix);
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
			setDynamicMatrix(newMatrix);
			return true;
		}

		@Override
		public void onScaleEnd(android.view.ScaleGestureDetector detector) {
			_scaling = false;
		}
	}

}
