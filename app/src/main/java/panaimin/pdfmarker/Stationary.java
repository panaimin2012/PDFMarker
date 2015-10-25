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

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.SparseArray;

// pure static class provides current holding stationary and it's paint

class Stationary {
	
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
	
	static Paint getPaint(int stationary) {
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
