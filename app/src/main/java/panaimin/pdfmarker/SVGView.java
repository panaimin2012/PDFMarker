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

import android.content.Context;
import android.graphics.Bitmap;
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
	static final int FLING_WIDTH = 150;
	static final int BG_COLOR = 0xFFF0F0F0;

	private int					_width = 0;
	private int					_height = 0;
	SVGRecorder					_svgRecorder;
	private Bitmap				_pdf;
	private SVGRecorder.SVGPath _currentPath;
	private PageActivity		_activity;
	private int					_fileId;
	private int					_pageId = -1;

	public SVGView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public SVGView(Context context) {
		super(context);
		initView();
	}

	void initView() {
		_activity = (PageActivity)getContext();
		_scaleListener = new ScaleListener();
		_scaleDetector = new ScaleGestureDetector(PDFMarkerApp.instance(), _scaleListener);
	}

	void setPage(int fileId, int pageId) {
		_fileId = fileId;
		_pageId = pageId;
		if (_width > 0)
			refresh();
	}

	@Override
	public void onSizeChanged(int w, int h, int oldW, int oldH) {
		_width = w;
		_height = h;
		SVGRecorder.setSize(_width, _height);
		if (_pageId >= 0)
			refresh();
	}

	void refresh() {
		_svgRecorder = new SVGRecorder(_fileId, _pageId);
		if (this == _activity._pageTurner._current)
			_pdf = PDFMaster.instance().gotoPage(_pageId);
		else if (this == _activity._pageTurner._previous)
			_pdf = PDFMaster.instance().getCachedBitmap(PDFMaster.PAGE_PREVIOUS);
		else if (this == _activity._pageTurner._next)
			_pdf = PDFMaster.instance().getCachedBitmap(PDFMaster.PAGE_NEXT);
		_fixedMatrix = PDFMaster.instance().getFixedMatrix(_width, _height);
		setDynamicMatrix(_dynamicMatrix);
	}

	private Bitmap _bgBmp = null;
	private Canvas _bgCanvas = null;
	private BitmapDrawable _bgDrawable = null;

	void setDynamicMatrix(Matrix matrix) {
		_dynamicMatrix = matrix;
		_dynamicMatrixValues = null;
		if(_fixedMatrix == null)
			return;
		Matrix _displayMatrix = new Matrix(_fixedMatrix);
		if(_dynamicMatrix != null)
			_displayMatrix.postConcat(_dynamicMatrix);
		if (_bgBmp == null) {
			Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
			_bgBmp = Bitmap.createBitmap(_width, _height, conf); // this creates a MUTABLE bitmap
			_bgCanvas = new Canvas(_bgBmp);
		}
		_bgCanvas.drawColor(BG_COLOR);
		_bgCanvas.drawBitmap(_pdf, _displayMatrix, null);
		if (_bgDrawable == null) {
			_bgDrawable = new BitmapDrawable(getResources(), _bgBmp);
			setBackground(_bgDrawable);
		}
		postInvalidate();
	}

	void cutEdge() {
		if(_dynamicMatrix != null) {
			PDFMaster.instance().cutEdge(_dynamicMatrix);
			_scale = 1.0f;
			setDynamicMatrix(null);
			refresh();
			Utility.instance().showToast(getResources().getString(R.string.msg_cut));
		}
	}

	@Override
	public void draw(@NonNull Canvas canvas) {
		super.draw(canvas);
	}

	@Override
	public void onDraw(Canvas canvas) {
		// draw svg
		if(_dynamicMatrix != null)
			canvas.concat(_dynamicMatrix);
		if (_svgRecorder != null)
			_svgRecorder.draw(canvas);
		if(_currentPath != null) {
			canvas.drawPath(_currentPath, Stationary.getCurrentPaint());
		}
		if(_dynamicMatrix != null)
			canvas.restore();
	}

	private float _flingStartX = -1;
	private float _flingStartY = -1;

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		int action = event.getAction() & MotionEvent.ACTION_MASK;
		if (_activity._markMode) {
			switch(action) {
			case MotionEvent.ACTION_DOWN :
				_activity.showMode();
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
						_activity.showCut();
						// fall through
					case MotionEvent.ACTION_POINTER_DOWN :
						_moving = false;
						break;
					}
				}
				_flingStartX = -1;
				_flingStartY = -1;
			}
			else if (action == MotionEvent.ACTION_DOWN) {
				_flingStartX = event.getX();
				_flingStartY = event.getY();
			}
			else if (action == MotionEvent.ACTION_UP) {
				if (_flingStartX > 0) {
					if (event.getX() - _flingStartX > FLING_WIDTH) {
						_activity._pageTurner.turnPage(PageTurner.TURNING_PREV_AUTO);
					}
					else if (_flingStartX - event.getX() > FLING_WIDTH) {
						_activity._pageTurner.turnPage(PageTurner.TURNING_NEXT_AUTO);
					}
					else if (_flingStartY < FLING_WIDTH)
						_activity.showMode();
					else {
						GotoDialog dlg = new GotoDialog(_activity);
						dlg.setPage(PDFMaster.instance().currentPage() + 1);
						dlg.show();
					}
				}
				_flingStartX = -1;
				_flingStartY = -1;
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
