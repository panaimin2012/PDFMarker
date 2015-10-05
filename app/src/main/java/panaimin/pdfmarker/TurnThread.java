package panaimin.pdfmarker;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;

// Thread that draws on an off screen bitmap
// setBitmaps: set the target off screen bitmap, left source and right source
// setTargetLeftMost: called when turn page forward, with parameter the left most point x
// setTargetRightMost: called when turn page backward, with parameter the right most point x
// setMode: called at EVENT_UP, to finish the page turn or cancel it

public class TurnThread extends Thread {
	
	private static final String	TAG = "THREAD";
	
	public static final int		DIR_NONE = 0;
	public static final int		DIR_FORWARD = 1;
	public static final int		DIR_BACKWARD = -1;
	
	public static final int		STATUS_IDLING = 0;
	public static final int		STATUS_WAITING = 1;
	public static final int		STATUS_RUNNING = 2;

	private static final int	MOVE_TOLERANCE = 8;
	private static final int	LEFT_SHADOW_DARK = 0xff404040;
	private static final int[]	LEFT_SHADOW_COLORS = new int[] { 0, LEFT_SHADOW_DARK };
	private static final int[]	RIGHT_SHADOW_COLORS = new int[] { 0xff000000, 0};
	private static final int[]	BACK1_COLORS = new int[] { 0xffd0d0d0, 0xff404040 };
	
	public TurnThread(PageView page) {
		_page = page;
		// init drawables
		_leftShadow = new GradientDrawable(Orientation.LEFT_RIGHT, LEFT_SHADOW_COLORS);
		_leftShadow.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		_rightShadow = new GradientDrawable(Orientation.LEFT_RIGHT, RIGHT_SHADOW_COLORS);
		_rightShadow.setGradientType(GradientDrawable.LINEAR_GRADIENT);
		_leftBack = new GradientDrawable(Orientation.LEFT_RIGHT, BACK1_COLORS);
		_leftBack.setGradientType(GradientDrawable.LINEAR_GRADIENT);
	}
	
	public void setBitmaps(Bitmap left, Bitmap right) {
		_leftBmp = left;
		_rightBmp = right;
		_width = _leftBmp.getWidth();
		_height = _leftBmp.getHeight();
		_radius = (float)(_width / (Math.PI * 2 + 2));
		// init map from pageTop to orient / angle
		_leftMost2Orient = new int[_width];
		_leftMost2Orient_minus = new int[_width];
		_leftMost2Angle = new float[_width];
		_leftMost2Angle_minus = new float[_width];
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
			while(lastLeftMost > leftMost) {
				lastLeftMost--;
				if(lastLeftMost < 0) {
					_leftMost2Orient_minus[-lastLeftMost] = orient;
					_leftMost2Angle_minus[-lastLeftMost] = angle;
				} else {
					_leftMost2Orient[lastLeftMost] = orient;
					_leftMost2Angle[lastLeftMost] = angle;
				}
			}
		}
	}
	
	public void terminate() {
		_stopped = true;
	}
	
	public int status() { return _status; }
	
	public int autoDirection() { return _autoDirection; }
	
	public void kickOff(int direction, int autoDirection) {
		if(direction == DIR_FORWARD) {
			setTargetLeftMost(_width - 1);
			setRightMost(_width - 2);
		} else {
			setTargetRightMost(2);
			setRightMost(1);
		}
		_lastLeftMost = _leftMost;
		_lastRightMost = _rightMost;
		if(autoDirection == DIR_BACKWARD)
			_targetRightMost = _width - 1;
		else if(autoDirection == DIR_FORWARD)
			_targetRightMost = 0;
		_autoDirection = autoDirection;
		status(STATUS_RUNNING);
		if(getState() == Thread.State.NEW)
			start();
	}
	
	public void setAutoDirection(int dir) {
		// set targetRightMost BEFORE setting mode
		if(dir == DIR_BACKWARD)
			_targetRightMost = _width - 1;
		else if(dir == DIR_FORWARD)
			_targetRightMost = 0;
		_autoDirection = dir;
		status(STATUS_RUNNING);
	}
	
	// setTargetLeftMost is called when manually turning forward (current page is being dragged to left)
	public void setTargetLeftMost(float leftMost) {
		float targetLeftMost = Math.max(Math.min(leftMost, _width - 1), -_width + 1);
		int orient = targetLeftMost >= 0 ? _leftMost2Orient[(int)targetLeftMost] :
			_leftMost2Orient_minus[(int)-targetLeftMost];
		float targetRightMost =
			orient > _width - _radius * Math.PI / 2 ? targetLeftMost :
			orient > 0 ? orient + _radius : (float)((_width + targetLeftMost) / (Math.PI * 2 - 2));
		setTargetRightMost(targetRightMost);
	}
	
	// setTargetRightMost is called when manually turning backward (previous page is being dragged to right)
	public void setTargetRightMost(float rightMost) {
		_targetRightMost = rightMost;
		if(_status == STATUS_WAITING) // if STATUS_IDLING, kickOff will change status
			status(STATUS_RUNNING);
	}
	
	public synchronized void status(int newStatus) {
		if(_status != newStatus) {
			_status = newStatus;
			if(_status == STATUS_RUNNING)
				interrupt();
		}
	}
	
	@Override
	public void run() {
		while(!_stopped) {
			try {
				while(_status == STATUS_IDLING)
					sleep(4000);
			} catch (InterruptedException e1) {
			}
			while(!_stopped && (
					_autoDirection == DIR_NONE || (_rightMost < _width - 1 && _rightMost > 0))) {
				if(_autoDirection == DIR_NONE && Math.abs(_rightMost - _targetRightMost) < MOVE_TOLERANCE) {
					status(STATUS_WAITING);
				}
				try {
					while(_status == STATUS_WAITING)
						sleep(4000);
				} catch (InterruptedException e1) {
				}
				if(_autoDirection == DIR_BACKWARD || _targetRightMost - _rightMost >= MOVE_TOLERANCE)
					setRightMost(_rightMost + MOVE_TOLERANCE);
				else if(_autoDirection == DIR_FORWARD || _rightMost - _targetRightMost >= MOVE_TOLERANCE)
					setRightMost(_rightMost - MOVE_TOLERANCE);
				if(_stopped)
					break;
				generate();
				_page.postInvalidate();
			}
			if(_stopped)
				break;
			recycleBitmaps();
			status(STATUS_IDLING);
			_page.turnFinished();
		}
	}
	
	private void recycleBitmaps() {
		_leftBmp.recycle();
		_rightBmp.recycle();
		_leftBmp = null;
		_rightBmp = null;
	}
	
	private PageView			_page;
	private int					_status = STATUS_IDLING;
	private boolean				_stopped = false;
	private Bitmap				_leftBmp;
	private Bitmap				_rightBmp;
	private int					_width;
	private int					_height;
	private float				_radius;
	// I try to simulate pulling the page edge
	// but unfortunately solving this equation is an advanced topic
	// angle = ( WIDTH - orient ) / RADIUS
	// leftMost = orient + sin(angle)
	// so I use a map from leftMost to orient to simulate
	private int[]				_leftMost2Orient;
	private float[]				_leftMost2Angle;
	// the leftMost point is from -_width to +_width, so use 2 array (try not to use map)
	private int[]				_leftMost2Orient_minus;
	private float[]				_leftMost2Angle_minus;
	private GradientDrawable	_leftShadow;
	private GradientDrawable	_rightShadow;
	private GradientDrawable	_leftBack;
	// these are calculated by UI action
	private int					_autoDirection = DIR_NONE;
	private float				_targetRightMost;
	// these are calculated by thread
	private float				_leftMost = -8000;
	private float				_rightMost = -8000;
	private int					_orient;

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
	}
	
	private float	_lastRightMost = 0f;
	private float	_lastLeftMost = 0f;
	private Rect	_dirtyRect = new Rect();
	private Path	_path = new Path();
	// rectangle that holds the arc
	private RectF	_rect1 = new RectF();
	private RectF	_rect2 = new RectF();
	private Rect	_sourceRect = new Rect();
	private RectF	_destRect = new RectF();

	// generate bitmap for _rightMost
	private void generate() {
		if(!_page.goodSurface()) {
			return;
		}
		if(_rightMost <= 0 || _rightMost >= _width - 1) {
			return;
		}
		int rightShadowWidth = (int)(_rightMost < _radius ? _rightMost : _radius) * 2;
		_dirtyRect.set((int)Math.min(Math.min(_lastLeftMost, _leftMost), _orient), 0,
			(int)Math.min(_lastRightMost, _rightMost) + rightShadowWidth, _height);
		Canvas canvas = _page.getHolder().lockCanvas(_dirtyRect);
		if(canvas == null) {
			return;
		}
		float angle = _leftMost >= 0 ? _leftMost2Angle[(int)_leftMost] : _leftMost2Angle_minus[(int)-_leftMost];
		float degree = (float)(angle * 180 / Math.PI);
		float _leftMostY = _leftMost > 0 ? _height - _radius + _radius * (float)Math.cos(angle) : _height;
		if(_rightMost < _radius)
			_rect1.set(-_rightMost, _height - _rightMost * 2, _rightMost, _height);
		else
			_rect1.set(_orient - _radius, _height - _radius * 2, _orient + _radius, _height);
		_rect2.set(_orient - _radius * 3, _height - _radius * 2, _orient - _radius, _height);
		// draw left bitmap -- unshadowed part
		if(_leftMost > 0 && _leftMost > _dirtyRect.left) {
			_path.reset();
			_path.moveTo(_dirtyRect.left, 0);
			_path.lineTo(_leftMost, 0);
			_path.lineTo(_leftMost, _height);
			_path.lineTo(_dirtyRect.left, _height);
			_path.close();
			canvas.save();
			canvas.clipPath(_path);
			_sourceRect.set(_dirtyRect.left, 0, (int)_leftMost, _height);
			_destRect.set(_sourceRect);
			canvas.drawBitmap(_leftBmp, _sourceRect, _destRect, null);
			canvas.restore();
		}
		// draw the right bitmap
		_path.reset();
		if(_rightMost < _radius || degree > 90) {
			_path.moveTo(_rightMost, 0);
			_path.lineTo(_rightMost, _height - Math.min(_radius, _rightMost));
			_path.arcTo(_rect1, 0, 90);
		} else {
			_path.moveTo(_leftMost, 0);
			_path.lineTo(_leftMost, _leftMostY);
			_path.arcTo(_rect1,  90 - degree, degree);
		}
		_path.lineTo(_dirtyRect.right, _height);
		_path.lineTo(_dirtyRect.right, 0);
		_path.close();
		canvas.save();
		canvas.clipPath(_path);
		_sourceRect.set(_orient, 0, _dirtyRect.right, _height);
		_destRect.set(_sourceRect);
		canvas.drawBitmap(_rightBmp, _sourceRect, _destRect, null);
		canvas.restore();
		// draw the left page back
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
			// part 1
			_path.reset();
			if(_rightMost < _radius) {
				_path.moveTo(0, 0);
				_path.lineTo(0, _height - _rightMost * 2);
				_path.arcTo(_rect1, -90, 90);
				_path.lineTo(_rightMost, _height - _rightMost);
				_path.lineTo(_rightMost, 0);
			} else if(degree <= 180) {
				_path.moveTo(_leftMost, 0);
				_path.lineTo(_leftMost, _leftMostY);
				_path.arcTo(_rect1, 90 - degree, degree - 90);
				_path.lineTo((int)Math.ceil(_orient + _radius), _height - _radius);
				_path.lineTo((int)Math.ceil(_orient + _radius), 0);
			} else {
				_path.moveTo(_orient, 0);
				_path.lineTo(_orient, _height - _radius * 2);
				_path.arcTo(_rect1, -90, 90);
				_path.lineTo((int)Math.ceil(_orient + _radius), _height - _radius);
				_path.lineTo((int)Math.ceil(_orient + _radius), 0);
			}
			_path.close();
			canvas.save();
			canvas.clipPath(_path);
			_leftBack.setOrientation(Orientation.LEFT_RIGHT);
			if(_orient > 0)
				_leftBack.setBounds(_orient, 0, (int)(_orient + _radius), (int)(_height - _radius));
			else
				_leftBack.setBounds(0, 0, (int)_rightMost, (int)(_height - _rightMost));
			_leftBack.draw(canvas);
			canvas.restore();
			if(degree > 180 && _orient > 0) {
				// part 2
				_path.reset();
				if(degree <= 270) {
					_path.moveTo(_leftMost, 0);
					_path.lineTo(_leftMost, _leftMostY);
					_path.arcTo(_rect1, 90 - degree, degree - 180);
				} else {
					_path.moveTo(_orient - _radius, 0);
					_path.lineTo(_orient - _radius, _height - _radius);
					_path.arcTo(_rect1, -180, 90);
				}
				_path.lineTo(_orient, _height - _radius * 2);
				_path.lineTo(_orient, 0);
				_path.close();
				canvas.save();
				canvas.clipPath(_path);
				_leftBack.setOrientation(Orientation.RIGHT_LEFT);
				_leftBack.setBounds((int)(_orient - _radius), 0, _orient, (int)(_height - _radius));
				_leftBack.draw(canvas);
				canvas.restore();
				if(degree > 270 && _orient > _radius) {
					// part 3
					_path.reset();
					_path.moveTo(_leftMost, 0);
					_path.lineTo(_orient - _radius, 0);
					_path.lineTo(_orient - _radius, _height - _radius);
					_path.arcTo(_rect2, 0, degree - 270);
					_path.lineTo(_leftMost, _leftMostY);
					_path.close();
					canvas.save();
					canvas.clipPath(_path);
					_leftBack.setOrientation(Orientation.LEFT_RIGHT);
					_leftBack.setBounds((int)(_orient - _radius * 2), 0, (int)(_orient - _radius), (int)_leftMostY);
					_leftBack.draw(canvas);
					canvas.restore();
				}
			}
		}
		// right shadow
		_path.reset();
		if(_rightMost < _radius) {
			_path.moveTo(_rightMost, 0);
			_path.lineTo(_rightMost, _height - _rightMost);
			_path.arcTo(_rect1, 0, 90);
			_path.lineTo(0, _height);
			_path.lineTo(rightShadowWidth, _height);
			_path.lineTo(rightShadowWidth , 0);
		} else if(degree <= 90) {
			_path.moveTo(_leftMost, 0);
			_path.lineTo(_leftMost, _leftMostY);
			_path.arcTo(_rect1, (float)(90 - degree), degree);
			_path.lineTo(_orient + rightShadowWidth, _height);
			_path.lineTo(_orient + _radius * 2, 0);
		} else {
			_path.moveTo((int)Math.floor(_orient + _radius), 0);
			_path.lineTo((int)Math.floor(_orient + _radius), _height - _radius);
			_path.arcTo(_rect1, 0, 90);
			_path.lineTo(_orient + rightShadowWidth, _height);
			_path.lineTo(_orient + rightShadowWidth, 0);
		}
		_path.close();
		canvas.save();
		canvas.clipPath(_path);
		_rightShadow.setOrientation(Orientation.LEFT_RIGHT);
		_rightShadow.setBounds(_orient, 0, _orient + rightShadowWidth, _height);
		_rightShadow.draw(canvas);
		canvas.restore();
		// draw left bitmap -- shadowed part
		_path.reset();
		int rectY1 = (int)(_height - _radius * 2);
		int rectX2 = (int)(_orient + _radius);
		if(_leftMost > 0) {
			if(degree > 270) {
				_path.moveTo(_leftMost, _leftMostY);
				_path.arcTo(_rect2, degree - 270, 270 - degree);
				_path.lineTo(_orient - _radius, _height - _radius);
				_path.arcTo(_rect1, -180, 270);
				_path.lineTo(_leftMost, _height);
			} else {
				_path.moveTo(_leftMost, _leftMostY);
				_path.arcTo(_rect1, 90 - degree, degree);
				if(degree > 180)
					_path.lineTo(_leftMost, _height);
			}
		} else if(_orient > _radius) {
			_path.moveTo(_orient - _radius * 2, _height);
			_path.arcTo(_rect2, 90, -90);
			_path.lineTo(_orient - _radius, _height - _radius);
			_path.arcTo(_rect1, -180, 270);
		} else if(_orient > 0) {
			_path.moveTo(_orient - _radius, _height - _radius);
			_path.arcTo(_rect1,  -180, 270);
			_path.lineTo(_orient - _radius, _height);
		} else {
			_path.moveTo(0, _height - _rightMost * 2);
			_path.arcTo(_rect1, -90, 180);
			rectY1 = (int)(_height - _rightMost * 2);
			rectX2 = (int)_rightMost;
		}
		_path.close();
		canvas.save();
		canvas.clipPath(_path);
		_sourceRect.set((int)Math.max(0f, _leftMost), rectY1, rectX2, _height);
		_destRect.set(_sourceRect);
		canvas.drawBitmap(_leftBmp, _sourceRect, _destRect, null);
		canvas.restore();
		// left shadow
		if(degree > 90 || (degree == 0 && _rightMost < _radius)) {
			_path.reset();
			float leftShadowStart = 0f;
			if(_rightMost < _radius) {
				_path.moveTo(0, _height - _rightMost * 2);
				_path.arcTo(_rect1, -90, 180);
				_path.lineTo(0, _height);
				leftShadowStart = -_rightMost * 2;
			} else if(_leftMost > 0) {
				_path.moveTo(_leftMost, _leftMostY);
				if(degree > 270) {
					_path.arcTo(_rect2, degree - 270, 270 - degree);
					_path.lineTo(_orient - _radius, _height - _radius);
					_path.arcTo(_rect1, -180, 270);
				} else {
					_path.arcTo(_rect1, 90 - degree, degree);
				}
				_path.lineTo(_leftMost, _height);
				leftShadowStart = _leftMost;
			} else {
				_path.moveTo(_orient - _radius * 2, _height);
				_path.arcTo(_rect2, 90, -90);
				_path.arcTo(_rect1, -180, 270);
				leftShadowStart = _orient - _radius * 2;
			}
			_path.close();
			canvas.save();
			canvas.clipPath(_path);
			_leftShadow.setOrientation(Orientation.LEFT_RIGHT);
			_leftShadow.setBounds((int)leftShadowStart, (int)(_height - _radius * 2), (int)_rightMost, _height);
			_leftShadow.draw(canvas);
			canvas.restore();
		}
		_page.getHolder().unlockCanvasAndPost(canvas);
		_lastRightMost = _rightMost;
		_lastLeftMost = _leftMost;
	}
	
}
