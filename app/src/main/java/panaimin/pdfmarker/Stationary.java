package panaimin.pdfmarker;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.SparseArray;

// pure static class provides current holding stationary and it's paint

public class Stationary {
	
	// main types
	static public final int	PENCIL = 1;
	static public final int	ERASER = 2;
	static public final int	HIGH_LIGHTER = 3;
	static public final int	COLOR_PENCIL = 4;
	
	// stroke width
	static public final int STROKE_PENCIL = 2;
	static public final int STROKE_ERASER = 20;
	static public final int STROKE_HIGH_LIGHTER = 30;
	static public final int STROKE_COLOR_PENCIL = 2;
	
	// sub types
	static public final int	P_2H = 1;
	static public final int	P_1H = 2;
	static public final int	P_HB = 3;
	static public final int	P_1B = 4;
	static public final int	P_2B = 5;
	static public final int	E_REGULAR = 1;
	static public final int	E_SUPER = 2;
	static public final int	HL_YELLOW = 1;
	static public final int	HL_GREEN = 2;
	static public final int	HL_PINK = 3;
	
	// I use one integer to represent stationary, assuming that there will never be
	// over 1M (0x1000000) sub type for one main type
	// stationary / 1M is the main type: PENCIL or ERASER or HIGH_LIGHTER or COLOR_PENCIL
	// stationary % 1M is the sub type
	// e.g., HB pencil = 0x1000003 = PENCIL * 1M + P_HB
	static public final int M = 0x1000000;
	static public int getCurrentStationary() { return _stationary; }
	static public Paint getCurrentPaint() { return getPaint(_stationary); }
	static public void setCurrentStationary(int stationary) { _stationary = stationary; }
	
	static public Paint getPaint(int stationary) {
		if(_paints.get(stationary) != null)
			return _paints.get(stationary);
		Paint paint = new Paint();
		_paints.put(stationary, paint);
		paint.setAntiAlias(true);
		paint.setDither(true);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeJoin(Paint.Join.ROUND);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setXfermode(null);
		paint.setAlpha(0xFF);
		int mainType = stationary / M;
		int subType = stationary % M;
		switch(mainType) {
		case PENCIL:
			paint.setColor(subType == P_2H ? Color.LTGRAY :
				subType == P_1H ? Color.GRAY :
				subType == P_HB ? Color.DKGRAY :
				subType == P_1B ? Color.argb(0xff, 0x22, 0x22, 0x22) :
				Color.BLACK);
			paint.setStrokeWidth(STROKE_PENCIL);
			break;
		case COLOR_PENCIL :
			paint.setColor(0xff000000 | subType);
			paint.setStrokeWidth(STROKE_COLOR_PENCIL);
			break;
		case ERASER :
			paint.setARGB(0xff, 0x80, 0x80, 0x80);
			paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			paint.setStrokeWidth(STROKE_ERASER);
			break;
		case HIGH_LIGHTER:
			int color = subType == HL_YELLOW ? Color.argb(0x44, 0xff, 0xff, 0) :
				subType == HL_GREEN ? Color.argb(0x44, 0, 0xff, 0) :
				Color.argb(0x44, 0xff, 0, 0xff);
			paint.setColor(color);
			paint.setStrokeWidth(STROKE_HIGH_LIGHTER);
			break;
		}
		return paint;
	}
	
	// private
	
	static private int					_stationary = PENCIL * M + P_HB;
	static private SparseArray<Paint>	_paints = new SparseArray<Paint>();
	
}
