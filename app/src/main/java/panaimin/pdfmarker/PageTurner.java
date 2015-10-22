package panaimin.pdfmarker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Scroller;

public class PageTurner extends FrameLayout {

	static final String TAG = "PDFMarker.Turner";

	private static final int	TURNING_NONE = 0;
	private static final int	TURNING_NEXT_MANUAL = 1;
	private static final int	TURNING_NEXT_AUTO = 2;
	private static final int	TURNING_PREV_MANUAL = -1;
	private static final int	TURNING_PREV_AUTO = -2;
	private static final int	EDGE_WIDTH = 50;
	private static final int	LEFT_SHADOW_DARK = 0xff404040;
	private static final int[]	LEFT_SHADOW_COLORS = new int[] { 0, LEFT_SHADOW_DARK };
	private static final int[]	RIGHT_SHADOW_COLORS = new int[] { 0xff000000, 0};
	private static final int[]	BACK1_COLORS = new int[] { 0xffd0d0d0, 0xff404040 };
	private static final int	SCROLL_TIME = 2000;

	// I try to simulate pulling the page edge
	// but unfortunately solving this equation is an advanced topic
	// angle = ( WIDTH - orient ) / RADIUS
	// leftMost = orient + sin(angle)
	// so I use a map from leftMost to orient to simulate
	private static int[]		_leftMost2Orient = null;
	private static float[]		_leftMost2Angle = null;
	// the leftMost point is from -_width to +_width, so use 2 array (try not to use map)
	private int[]				_leftMost2Orient_minus = null;
	private float[]				_leftMost2Angle_minus = null;

	private PageActivity		_activity;
	SVGView						_current;
	SVGView						_previous;
	SVGView						_next;
	private int					_pageId;
	private int					_turning = TURNING_NONE;
	private GradientDrawable 	_leftShadow;
	private GradientDrawable	_rightShadow;
	private GradientDrawable	_leftBack;
	private Scroller			_scroller;
	private int					_width;
	private int					_height;
	private float				_radius;
	private float				_leftMost;
	private float				_rightMost;
	private int					_orient;

	public PageTurner(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public PageTurner(Context context) {
		super(context);
	}

	void initView() {
		_activity = (PageActivity)getContext();
		int fileId = _activity._fileId;
		_pageId = _activity._pageId;
		// current
		_current = new SVGView(_activity);
		addView(_current);
		_current.setPage(fileId, _pageId);
		// previous
		_previous = new SVGView(_activity);
		_previous.setVisibility(View.INVISIBLE);
		addView(_previous);
		if (_pageId > 0)
			_previous.setPage(fileId, _pageId - 1);
		// next
		_next = new SVGView(_activity);
		_next.setVisibility(View.INVISIBLE);
		addView(_next);
		if (_pageId < PDFMaster.instance().countPages() - 1)
			_next.setPage(fileId, _pageId + 1);
		// init drawables
		_leftShadow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, LEFT_SHADOW_COLORS);
		_leftShadow.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		_rightShadow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, RIGHT_SHADOW_COLORS);
		_rightShadow.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		_leftBack = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, BACK1_COLORS);
		_leftBack.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		_scroller = new Scroller(_activity);
	}

	@Override
	public void onSizeChanged(int width, int height, int oldW, int oldH) {
		_width = width;
		_height = height;
		initView();
		_radius = (float)(_width / (Math.PI * 2 + 2));
		if (_leftMost2Angle != null)
			return;
		// init map from pageTop to orient / angle
		_leftMost2Orient = new int[_width];
		_leftMost2Orient_minus = new int[_width];
		_leftMost2Angle = new float[_width];
		_leftMost2Angle_minus = new float[_width];
		LogDog.i(TAG, "onSizeChanged _leftMost2Angle_minus size=" + _leftMost2Angle_minus.length);
		int lastLeftMost = _width - 1;
		_leftMost2Orient[lastLeftMost] = _width - 1;
		_leftMost2Angle[lastLeftMost] = 0;
		for(int orient = _width - 1; orient >= 0; --orient) {
			int s = _width - orient;
			float angle = (float)s / _radius;
			float degree = (float)(angle * 180 / Math.PI);
			int leftMost =
				degree > 360 ? (int)(Math.PI * 2 * _radius  + orient * 2 - _radius * 2 - _width) :
					degree > 270 ? (int)(orient - _radius * 2 - _radius * Math.sin(angle)) :
						(int) (orient + _radius * Math.sin(angle));
			while (lastLeftMost > leftMost) {
				lastLeftMost--;
				if (lastLeftMost < 0) {
					_leftMost2Orient_minus[-lastLeftMost] = orient;
					_leftMost2Angle_minus[-lastLeftMost] = angle;
				} else {
					_leftMost2Orient[lastLeftMost] = orient;
					_leftMost2Angle[lastLeftMost] = angle;
				}
			}
		}
	}

	// setLeftMost is called when manually turning to next page
	public void setLeftMost(float leftMost) {
		//float targetLeftMost = Math.max(Math.min(leftMost, _width - 1), -_width + 1);
		_leftMost = leftMost;
		int orient = _leftMost >= 0 ? _leftMost2Orient[(int)_leftMost] :
			_leftMost2Orient_minus[(int)-_leftMost];
		float rightMost =
			orient > _width - _radius * Math.PI / 2 ? leftMost :
				orient > 0 ? orient + _radius : (float)((_width + leftMost) / (Math.PI * 2 - 2));
		setRightMost(rightMost);
	}

	private void setRightMost(float rightMost) {
		LogDog.i(TAG, "setRightMost " + rightMost);
		_rightMost = rightMost;
		if(_rightMost < _radius) {
			_orient = 0;
			_leftMost = Math.max((float)Math.PI * _rightMost * 2 - _width - _rightMost * 2, 1 - _width);
		} else if (_rightMost > _width - Math.PI * _radius / 2 + _radius) {
			if(_rightMost > _width - 1)
				_rightMost = _width - 1;
			_leftMost = _rightMost;
			_orient = _leftMost2Orient[(int)_leftMost];
		} else {
			_orient = (int) (_rightMost - _radius);
			int s = _width - _orient;
			float angle = (float)s / _radius;
			float degree = (float)(angle * 180 / Math.PI);
			_leftMost =
				degree > 360 ? (int)(_orient * 2 + Math.PI * 2 * _radius - _radius * 2 - _width) :
					degree > 270 ? (int)(_orient - _radius * 2 - _radius * Math.sin(angle)) :
						(int) (_orient + _radius * Math.sin(angle));
		}
		LogDog.i(TAG, "setRightMost leftMost=" + _leftMost);
		postInvalidate();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent ev) {
		float x = ev.getX();
		LogDog.i(TAG, "onTouchEvent " + ev.getActionMasked() + " " + x);
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_MOVE :
				if (_turning == TURNING_PREV_MANUAL)
					setRightMost(x);
				else if (_turning == TURNING_NEXT_MANUAL)
					setLeftMost(x);
				break;
			case MotionEvent.ACTION_UP :
				if (_turning == TURNING_PREV_MANUAL) {
					_turning = TURNING_PREV_AUTO;
					_scroller.startScroll((int) x, 0, _width - (int) x, 0, SCROLL_TIME);
				}
				else if (_turning == TURNING_NEXT_MANUAL) {
					_turning = TURNING_NEXT_AUTO;
					_scroller.startScroll((int) x, 0, (int) -x, 0, SCROLL_TIME);
				}
		}
		return true;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (_activity._markMode) {
			return false;
		}
		float x = ev.getX();
		LogDog.i(TAG, "onInterceptTouchEvent " + ev.getActionMasked() + " " + x);
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (x < EDGE_WIDTH) {
					_turning = TURNING_PREV_MANUAL;
					_previous.setVisibility(VISIBLE);
					setRightMost(x);
					return true;
				} else if (x > _width - EDGE_WIDTH) {
					_turning = TURNING_NEXT_MANUAL;
					_next.setVisibility(VISIBLE);
					setLeftMost(x);
					return true;
				}
				break;
		}
		return false;
	}

	@Override
	public void computeScroll() {
		LogDog.i(TAG, "computeScroll");
		super.computeScroll();
		if (_turning != TURNING_NEXT_AUTO && _turning != TURNING_PREV_AUTO)
			return;
		if (_scroller.computeScrollOffset()) {
			// scrolling
			float x = _scroller.getCurrX();
			LogDog.i(TAG, "computeScroll x=" + x);
			if (_turning == TURNING_PREV_AUTO) {
				setRightMost(x);
			}
			else if (_turning == TURNING_NEXT_AUTO) {
				setLeftMost(x);
			}
			postInvalidate();
		}
		else {
			// scroll finished
			_current.setVisibility(INVISIBLE);
			if (isTurningNext()) {
				SVGView v = _previous;
				_previous = _current;
				_current = _next;
				_next = v;
				_pageId ++;
				if (_pageId < PDFMaster.instance().countPages() - 1)
					_next.setPage(_activity._fileId, _pageId + 1);
			} else {
				SVGView v = _next;
				_next = _current;
				_current = _previous;
				_previous = v;
				_pageId --;
				if (_pageId > 0)
					_next.setPage(_activity._fileId, _pageId - 1);
			}
			_turning = TURNING_NONE;
		}
	}

	void turnToPage(int pageNumber) {
		int fileId = _activity._fileId;
		_pageId = pageNumber;
		_current.setPage(fileId, _pageId);
		if (_pageId > 0)
			_previous.setPage(fileId, _pageId - 1);
		if (_pageId < PDFMaster.instance().countPages() - 1)
			_next.setPage(fileId, _pageId + 1);
		_current.refresh();
		DB.instance().setLastPage(fileId, _pageId);
		PDFMarkerApp.instance().showToast("Page " + (_pageId + 1));
	}

	private RectF _rect1 = new RectF();
	private RectF _rect2 = new RectF();
	private float _lastLeftMost;
	private float _leftMostY;
	private float _lastRightMost;
	private float _leftShadowStart;
	private int   _rightShadowWidth;
	private Path  _pLeft = new Path();
	private Path  _pLeftShadow = new Path();
	private Path  _pRight = new Path();
	private Path  _pLeftBack1 = new Path();
	private Path  _pLeftBack2 = new Path();
	private Path  _pLeftBack3 = new Path();
	private Path  _pRightShadow = new Path();

	void calculatePaths() {
		_rightShadowWidth = (int)(_rightMost < _radius ? _rightMost : _radius) * 2;
		LogDog.i(TAG, "calculatePaths leftMost=" + _leftMost);
		float angle = _leftMost >= 0 ? _leftMost2Angle[(int)_leftMost] : _leftMost2Angle_minus[(int)-_leftMost];
		float degree = (float)(angle * 180 / Math.PI);
		_leftMostY = _leftMost > 0 ? _height - _radius + _radius * (float)Math.cos(angle) : _height;
		if(_rightMost < _radius)
			_rect1.set(-_rightMost, _height - _rightMost * 2, _rightMost, _height);
		else
			_rect1.set(_orient - _radius, _height - _radius * 2, _orient + _radius, _height);
		_rect2.set(_orient - _radius * 3, _height - _radius * 2, _orient - _radius, _height);
		// draw left bitmap
		_pLeft.reset();
		//int rectY1 = (int)(_height - _radius * 2);
		//int rectX2 = (int)(_orient + _radius);
		if(_leftMost > 0) {
			_pLeft.moveTo(0, 0);
			_pLeft.lineTo(_leftMost, 0);
			if(degree > 270) {
				_pLeft.lineTo(_leftMost, _leftMostY);
				_pLeft.arcTo(_rect2, degree - 270, 270 - degree);
				_pLeft.lineTo(_orient - _radius, _height - _radius);
				_pLeft.arcTo(_rect1, -180, 270);
				_pLeft.lineTo(_leftMost, _height);
			} else {
				_pLeft.lineTo(_leftMost, _leftMostY);
				_pLeft.arcTo(_rect1, 90 - degree, degree);
				if(degree > 180)
					_pLeft.lineTo(_leftMost, _height);
			}
			_pLeft.lineTo(0, _height);
		} else if(_orient > _radius) {
			_pLeft.moveTo(_orient - _radius * 2, _height);
			_pLeft.arcTo(_rect2, 90, -90);
			_pLeft.lineTo(_orient - _radius, _height - _radius);
			_pLeft.arcTo(_rect1, -180, 270);
		} else if(_orient > 0) {
			_pLeft.moveTo(_orient - _radius, _height - _radius);
			_pLeft.arcTo(_rect1,  -180, 270);
			_pLeft.lineTo(_orient - _radius, _height);
		} else {
			_pLeft.moveTo(0, _height - _rightMost * 2);
			_pLeft.arcTo(_rect1, -90, 180);
			//rectY1 = (int)(_height - _rightMost * 2);
			//rectX2 = (int)_rightMost;
		}
		_pLeft.close();
		// draw the right bitmap
		_pRight.reset();
		if(_rightMost < _radius || degree > 90) {
			_pRight.moveTo(_rightMost, 0);
			_pRight.lineTo(_rightMost, _height - Math.min(_radius, _rightMost));
			_pRight.arcTo(_rect1, 0, 90);
		} else {
			_pRight.moveTo(_leftMost, 0);
			_pRight.lineTo(_leftMost, _leftMostY);
			_pRight.arcTo(_rect1,  90 - degree, degree);
		}
		_pRight.lineTo(_width, _height);
		_pRight.lineTo(_width, 0);
		_pRight.close();
		// draw the left page back
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
			// part 1
			_pLeftBack1.reset();
			if(_rightMost < _radius) {
				_pLeftBack1.moveTo(0, 0);
				_pLeftBack1.lineTo(0, _height - _rightMost * 2);
				_pLeftBack1.arcTo(_rect1, -90, 90);
				_pLeftBack1.lineTo(_rightMost, _height - _rightMost);
				_pLeftBack1.lineTo(_rightMost, 0);
			} else if(degree <= 180) {
				_pLeftBack1.moveTo(_leftMost, 0);
				_pLeftBack1.lineTo(_leftMost, _leftMostY);
				_pLeftBack1.arcTo(_rect1, 90 - degree, degree - 90);
				_pLeftBack1.lineTo((int)Math.ceil(_orient + _radius), _height - _radius);
				_pLeftBack1.lineTo((int)Math.ceil(_orient + _radius), 0);
			} else {
				_pLeftBack1.moveTo(_orient, 0);
				_pLeftBack1.lineTo(_orient, _height - _radius * 2);
				_pLeftBack1.arcTo(_rect1, -90, 90);
				_pLeftBack1.lineTo((int)Math.ceil(_orient + _radius), _height - _radius);
				_pLeftBack1.lineTo((int)Math.ceil(_orient + _radius), 0);
			}
			_pLeftBack1.close();
			if(degree > 180 && _orient > 0) {
				// part 2
				_pLeftBack2.reset();
				if(degree <= 270) {
					_pLeftBack2.moveTo(_leftMost, 0);
					_pLeftBack2.lineTo(_leftMost, _leftMostY);
					_pLeftBack2.arcTo(_rect1, 90 - degree, degree - 180);
				} else {
					_pLeftBack2.moveTo(_orient - _radius, 0);
					_pLeftBack2.lineTo(_orient - _radius, _height - _radius);
					_pLeftBack2.arcTo(_rect1, -180, 90);
				}
				_pLeftBack2.lineTo(_orient, _height - _radius * 2);
				_pLeftBack2.lineTo(_orient, 0);
				_pLeftBack2.close();
				if(degree > 270 && _orient > _radius) {
					// part 3
					_pLeftBack3.reset();
					_pLeftBack3.moveTo(_leftMost, 0);
					_pLeftBack3.lineTo(_orient - _radius, 0);
					_pLeftBack3.lineTo(_orient - _radius, _height - _radius);
					_pLeftBack3.arcTo(_rect2, 0, degree - 270);
					_pLeftBack3.lineTo(_leftMost, _leftMostY);
					_pLeftBack3.close();
				}
			}
		}
		// right shadow
		_pRightShadow.reset();
		if(_rightMost < _radius) {
			_pRightShadow.moveTo(_rightMost, 0);
			_pRightShadow.lineTo(_rightMost, _height - _rightMost);
			_pRightShadow.arcTo(_rect1, 0, 90);
			_pRightShadow.lineTo(0, _height);
			_pRightShadow.lineTo(_rightShadowWidth, _height);
			_pRightShadow.lineTo(_rightShadowWidth , 0);
		} else if(degree <= 90) {
			_pRightShadow.moveTo(_leftMost, 0);
			_pRightShadow.lineTo(_leftMost, _leftMostY);
			_pRightShadow.arcTo(_rect1, 90 - degree, degree);
			_pRightShadow.lineTo(_orient + _rightShadowWidth, _height);
			_pRightShadow.lineTo(_orient + _radius * 2, 0);
		} else {
			_pRightShadow.moveTo((int)Math.floor(_orient + _radius), 0);
			_pRightShadow.lineTo((int)Math.floor(_orient + _radius), _height - _radius);
			_pRightShadow.arcTo(_rect1, 0, 90);
			_pRightShadow.lineTo(_orient + _rightShadowWidth, _height);
			_pRightShadow.lineTo(_orient + _rightShadowWidth, 0);
		}
		_pRightShadow.close();
		// left shadow
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
			_pLeftShadow.reset();
			_leftShadowStart = 0f;
			if(_rightMost < _radius) {
				_pLeftShadow.moveTo(0, _height - _rightMost * 2);
				_pLeftShadow.arcTo(_rect1, -90, 180);
				_pLeftShadow.lineTo(0, _height);
				_leftShadowStart = -_rightMost * 2;
			} else if(_leftMost > 0) {
				_pLeftShadow.moveTo(_leftMost, _leftMostY);
				if(degree > 270) {
					_pLeftShadow.arcTo(_rect2, degree - 270, 270 - degree);
					_pLeftShadow.lineTo(_orient - _radius, _height - _radius);
					_pLeftShadow.arcTo(_rect1, -180, 270);
				} else {
					_pLeftShadow.arcTo(_rect1, 90 - degree, degree);
				}
				_pLeftShadow.lineTo(_leftMost, _height);
				_leftShadowStart = _leftMost;
			} else {
				_pLeftShadow.moveTo(_orient - _radius * 2, _height);
				_pLeftShadow.arcTo(_rect2, 90, -90);
				_pLeftShadow.arcTo(_rect1, -180, 270);
				_leftShadowStart = _orient - _radius * 2;
			}
			_pLeftShadow.close();
		}
		_lastRightMost = _rightMost;
		_lastLeftMost = _leftMost;
	}

	@Override
	public void dispatchDraw(@NonNull Canvas canvas) {
		if (_turning != TURNING_NONE)
			calculatePaths();
		super.dispatchDraw(canvas);
		if (_turning == TURNING_NONE)
			return;
		// draw page back, which is not in any of the children
		// part 1
		canvas.save();
		canvas.clipPath(_pLeftBack1);
		_leftBack.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
		if(_orient > 0)
			_leftBack.setBounds(_orient, 0, (int)(_orient + _radius), (int)(_height - _radius));
		else
			_leftBack.setBounds(0, 0, (int)_rightMost, (int)(_height - _rightMost));
		_leftBack.draw(canvas);
		canvas.restore();
		// part 2
		canvas.save();
		canvas.clipPath(_pLeftBack2);
		_leftBack.setOrientation(GradientDrawable.Orientation.RIGHT_LEFT);
		_leftBack.setBounds((int) (_orient - _radius), 0, _orient, (int) (_height - _radius));
		_leftBack.draw(canvas);
		canvas.restore();
		// part 3
		canvas.save();
		canvas.clipPath(_pLeftBack3);
		_leftBack.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
		_leftBack.setBounds((int)(_orient - _radius * 2), 0, (int)(_orient - _radius), (int)_leftMostY);
		_leftBack.draw(canvas);
		canvas.restore();
		// right shadow
		canvas.save();
		canvas.clipPath(_pRightShadow);
		_rightShadow.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
		_rightShadow.setBounds(_orient, 0, _orient + _rightShadowWidth, _height);
		_rightShadow.draw(canvas);
		canvas.restore();
		// left shadow
		canvas.save();
		canvas.clipPath(_pLeftShadow);
		_leftShadow.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
		_leftShadow.setBounds((int)_leftShadowStart, (int)(_height - _radius * 2), (int)_rightMost, _height);
		_leftShadow.draw(canvas);
		canvas.restore();
	}

	private boolean isTurningNext() { return _turning > TURNING_NONE; }
	private boolean isTurningPrev() { return _turning < TURNING_NONE; }

	@Override
	public boolean drawChild(@NonNull Canvas canvas, @NonNull View child, long time) {
		if (_turning == TURNING_NONE)
			return super.drawChild(canvas, child, time);
		if (child == _current && isTurningNext()) {
			canvas.save();
			canvas.clipPath(_pLeft);
			child.draw(canvas);
			canvas.restore();
		}
		else if (child == _current && isTurningPrev()) {
			canvas.save();
			canvas.clipPath(_pRight);
			child.draw(canvas);
			canvas.restore();
		}
		else if (child == _next && isTurningNext()) {
			canvas.save();
			canvas.clipPath(_pRight);
			child.draw(canvas);
			canvas.restore();
		} else if (child == _previous && isTurningPrev()) {
			canvas.save();
			canvas.clipPath(_pLeft);
			child.draw(canvas);
			canvas.restore();
		}
		else
			return super.drawChild(canvas, child, time);
		return true;
	}

}
