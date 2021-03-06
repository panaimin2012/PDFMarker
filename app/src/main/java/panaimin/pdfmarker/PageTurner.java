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
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.GradientDrawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.Scroller;

public class PageTurner extends FrameLayout {

	static final String TAG = "PDFMarker.Turner";

	static final int	TURNING_NONE = 0;
	static final int	TURNING_NEXT_MANUAL = 1;
	static final int	TURNING_NEXT_AUTO = 2;
	static final int	TURNING_PREV_MANUAL = -1;
	static final int	TURNING_PREV_AUTO = -2;

	private static final int	EDGE_WIDTH = 50;
	private static final int	LEFT_SHADOW_DARK = 0xff404040;
	private static final int[]	LEFT_SHADOW_COLORS = new int[] { 0, LEFT_SHADOW_DARK };
	private static final int[]	RIGHT_SHADOW_COLORS = new int[] { 0xff000000, 0};
	private static final int[]	BACK1_COLORS = new int[] { 0xffd0d0d0, 0xff404040 };
	private static final float	SCROLL_TIME = 1000f;

	// I try to simulate pulling the page edge
	// but unfortunately solving this equation is an advanced topic
	// angle = ( WIDTH - orient ) / RADIUS
	// leftMost = orient + sin(angle)
	// so I use a map from leftMost to orient to simulate
	private static int[]		_leftMost2Orient = null;
	private static float[]		_leftMost2Angle = null;
	// the leftMost point is from -_width to +_width, so use 2 array (try not to use map)
	private static int[]		_leftMost2Orient_minus = null;
	private static float[]		_leftMost2Angle_minus = null;

	private PageActivity		_activity;
	SVGView						_current;
	SVGView						_previous;
	SVGView						_next;
	private int					_pageId;
	private int					_turning = TURNING_NONE;
	private GradientDrawable 	_leftShadow;
	private GradientDrawable	_rightShadow;
	private GradientDrawable	_leftBack;
	private Scroller			_scroller = null;
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
		if (!Utility.instance().getPref(PDFMarkerApp.PREF_HARDWARE_ACCELERATION, false))
			setLayerType(View.LAYER_TYPE_SOFTWARE, null);
		_activity = (PageActivity)getContext();
		_scroller = new Scroller(_activity, new LinearInterpolator());
		int fileId = _activity._fileId;
		_pageId = PDFMaster.instance().currentPage();
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
		int orient;
		float angle = 0;
		for(orient = _width - 1; orient >= 0; --orient) {
			int s = _width - orient;
			angle = (float)s / _radius;
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
		while (lastLeftMost > 1 - _width) {
			lastLeftMost --;
			_leftMost2Orient_minus[-lastLeftMost] = orient;
			_leftMost2Angle_minus[-lastLeftMost] = angle;
		}
		_leftMost2Orient_minus[0] = _leftMost2Orient[0];
		_leftMost2Angle_minus[0] = _leftMost2Angle[0];
	}

	// setLeftMost is called when manually turning to next page
	void setLeftMost(float leftMost) {
		//float targetLeftMost = Math.max(Math.min(leftMost, _width - 1), -_width + 1);
		_leftMost = leftMost;
		LogDog.i(TAG, "setLeftMost to " + leftMost);
		int orient = _leftMost >= 0 ? _leftMost2Orient[(int)_leftMost] :
			_leftMost2Orient_minus[(int)-_leftMost];
		float rightMost =
			orient > _width - _radius * Math.PI / 2 ? leftMost :
				orient > 0 ? orient + _radius : (float)((_width + leftMost) / (Math.PI * 2 - 2));
		setRightMost(rightMost);
	}

	private void setRightMost(float rightMost) {
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
		postInvalidate();
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent ev) {
		float x = ev.getX();
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
					int timeLeft = (int)(SCROLL_TIME / _width * x);
					LogDog.i(TAG, "Start scrolling prev from " + x + " in " + timeLeft + " ms");
					_scroller.startScroll((int) x, 0, _width - (int) x - 1, 0, timeLeft);
					postInvalidate();
				}
				else if (_turning == TURNING_NEXT_MANUAL) {
					_turning = TURNING_NEXT_AUTO;
					int timeLeft = (int)(SCROLL_TIME / (_width * 2) * (x + _width));
					LogDog.i(TAG, "Start scrolling next from " + x + " in " + timeLeft + " ms");
					_scroller.startScroll((int) x, 0, (int) (1 - x - _width), 0, timeLeft);
					postInvalidate();
				}
		}
		return true;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (_activity._markMode)
			return false;
		if (_turning != TURNING_NONE)
			return true;
		float x = ev.getX();
		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
				if (x < EDGE_WIDTH) {
					if (_pageId > 0) {
						_turning = TURNING_PREV_MANUAL;
						_previous.setVisibility(VISIBLE);
						setRightMost(x);
					} else
						Utility.instance().showToast(R.string.msg_firstpage);
					return true;
				} else if (x > _width - EDGE_WIDTH) {
					if (_pageId < PDFMaster.instance().countPages() - 1) {
						_turning = TURNING_NEXT_MANUAL;
						_next.setVisibility(VISIBLE);
						setLeftMost(x);
					} else
						Utility.instance().showToast(R.string.msg_lastpage);
					return true;
				}
				break;
		}
		return false;
	}

	@Override
	public void computeScroll() {
		super.computeScroll();
		if (_turning != TURNING_NEXT_AUTO && _turning != TURNING_PREV_AUTO)
			return;
		if (_scroller.computeScrollOffset()) {
			// scrolling
			float x = _scroller.getCurrX();
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
			int newPage;
			if (isTurningNext()) {
				SVGView v = _previous;
				_previous = _current;
				_current = _next;
				_next = v;
				newPage = _pageId + 1;
			} else {
				SVGView v = _next;
				_next = _current;
				_current = _previous;
				_previous = v;
				newPage = _pageId - 1;
			}
			_turning = TURNING_NONE;
			turnToPage(newPage);
		}
	}

	void turnPage(int direction) {
		boolean fast = Utility.instance().getPref(PDFMarkerApp.PREF_FAST_PAGE_TURN, false);
		if (direction == TURNING_PREV_AUTO) {
			if (_pageId > 0) {
				if (fast)
					turnToPage(_pageId - 1);
				else {
					_previous.setVisibility(VISIBLE);
					_turning = TURNING_PREV_AUTO;
					_scroller.startScroll(1, 0, _width - 2, 0, (int) SCROLL_TIME);
					postInvalidate();
				}
			}
			else
				Utility.instance().showToast(R.string.msg_firstpage);
		}
		else if (direction == TURNING_NEXT_AUTO) {
			if (_pageId < PDFMaster.instance().countPages() - 1) {
				if (fast)
					turnToPage(_pageId + 1);
				else {
					_next.setVisibility(VISIBLE);
					_turning = TURNING_NEXT_AUTO;
					_scroller.startScroll(_width - 1, 0, 2 - _width - _width, 0, (int) SCROLL_TIME);
					postInvalidate();
				}
			}
			else
				Utility.instance().showToast(R.string.msg_lastpage);
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
		Utility.instance().showToast("Page " + (_pageId + 1));
	}

	private RectF _rect1 = new RectF();
	private RectF _rect2 = new RectF();
	private float _leftMostY;
	private float _leftShadowStart;
	private int   _rightShadowWidth;
	private Path  _pLeftShadow = new Path();
	private Path  _pRight = new Path();
	private Path  _pLeftBack1 = new Path();
	private Path  _pLeftBack2 = new Path();
	private Path  _pLeftBack3 = new Path();
	private Path  _pRightShadow = new Path();

	private void calculatePaths() {
		_rightShadowWidth = (int)(_rightMost < _radius ? _rightMost : _radius) * 2;
		float angle = _leftMost >= 0 ? _leftMost2Angle[(int)_leftMost] : _leftMost2Angle_minus[(int)-_leftMost];
		float degree = (float)(angle * 180 / Math.PI);
		_leftMostY = _leftMost > 0 ? _height - _radius + _radius * (float)Math.cos(angle) : _height;
		if(_rightMost < _radius)
			_rect1.set(-_rightMost, _height - _rightMost * 2, _rightMost, _height);
		else
			_rect1.set(_orient - _radius, _height - _radius * 2, _orient + _radius, _height);
		_rect2.set(_orient - _radius * 3, _height - _radius * 2, _orient - _radius, _height);
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
		_pLeftBack1.reset();
		_pLeftBack2.reset();
		_pLeftBack3.reset();
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
			// part 1
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
				_pLeftBack1.lineTo((int)(_orient + _radius), _height - _radius);
				_pLeftBack1.lineTo((int)(_orient + _radius), 0);
			} else {
				_pLeftBack1.moveTo(_orient, 0);
				_pLeftBack1.lineTo(_orient, _height - _radius * 2);
				_pLeftBack1.arcTo(_rect1, -90, 90);
				_pLeftBack1.lineTo((int)(_orient + _radius), _height - _radius);
				_pLeftBack1.lineTo((int)(_orient + _radius), 0);
			}
			if(degree > 180 && _orient > 0) {
				// part 2
				if(degree <= 270) {
					_pLeftBack2.moveTo(_leftMost, 0);
					_pLeftBack2.lineTo(_leftMost, _leftMostY);
					_pLeftBack2.arcTo(_rect1, 90 - degree, degree - 180);
				} else {
					_pLeftBack2.moveTo((int)Math.floor(_orient - _radius), 0);
					_pLeftBack2.lineTo((int)Math.floor(_orient - _radius), _height - _radius);
					_pLeftBack2.arcTo(_rect1, -180, 90);
				}
				_pLeftBack2.lineTo(_orient, _height - _radius * 2);
				_pLeftBack2.lineTo(_orient, 0);
				if(degree > 270 && _orient > _radius) {
					// part 3
					_pLeftBack3.moveTo(_leftMost, 0);
					_pLeftBack3.lineTo((int) Math.ceil(_orient - _radius), 0);
					_pLeftBack3.lineTo((int) Math.ceil(_orient - _radius), _height - _radius);
					_pLeftBack3.arcTo(_rect2, 0, degree - 270);
					_pLeftBack3.lineTo(_leftMost, _leftMostY);
				}
			}
		}
		_pLeftBack1.close();
		_pLeftBack2.close();
		_pLeftBack3.close();
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
		_pLeftShadow.reset();
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
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
		}
		_pLeftShadow.close();
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
		_leftBack.setBounds((int)Math.floor(_orient - _radius), 0, _orient, (int) (_height - _radius));
		_leftBack.draw(canvas);
		canvas.restore();
		// part 3
		canvas.save();
		canvas.clipPath(_pLeftBack3);
		_leftBack.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
		_leftBack.setBounds((int)(_orient - _radius * 2), 0, (int)Math.ceil(_orient - _radius), (int)_leftMostY);
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
		if ((child == _current && isTurningNext()) || (child == _previous && isTurningPrev())) {
			// left bitmap = full range - right - left back 1 - left back 2 - left back 3
			canvas.save();
			canvas.clipPath(_pRight, Region.Op.DIFFERENCE);
			canvas.clipPath(_pLeftBack1, Region.Op.DIFFERENCE);
			canvas.clipPath(_pLeftBack2, Region.Op.DIFFERENCE);
			canvas.clipPath(_pLeftBack3, Region.Op.DIFFERENCE);
			child.draw(canvas);
			canvas.restore();
		}
		else if ((child == _current && isTurningPrev()) || (child == _next && isTurningNext())) {
			canvas.save();
			canvas.clipPath(_pRight);
			child.draw(canvas);
			canvas.restore();
		}
		else
			return super.drawChild(canvas, child, time);
		return true;
	}

}
