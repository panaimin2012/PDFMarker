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

import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.Xml;

class SVGRecorder {
	
	// static
	
	static final String		TAG = "PDFMarker.SVGRecorder";
	
	private static int				_width;
	private static int				_height;
	
	static public void setSize(int width, int height) {
		_width = width;
		_height = height;
	}
	
	static private final String		ENCODING = "UTF-8";
	static private final String		TAG_PATH = "PATH";
	static private final String		TAG_POINT = "POINT";
	static private final String		TAG_SIZE = "SIZE";
	static private final String		ATTRIBUTE_PEN = "PEN";
	static private final String		ATTRIBUTE_X = "X";
	static private final String		ATTRIBUTE_Y = "Y";
	
	SVGRecorder(int fileId, int pageId) {
		_fileId = fileId;
		_pageId = pageId;
		_paths = new ArrayList<>();
		load();
	}
	
	private void load() {
		_paths.clear();
		String svgFile = PDFMarkerApp.instance().getFilesDir().getPath() + "/" + _fileId + "." + _pageId + ".svg";
		XmlPullParserFactory factory;
		try {
			factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XmlPullParser xpp = factory.newPullParser();
			xpp.setInput(new FileInputStream(svgFile), ENCODING);
			int eventType = xpp.getEventType();
			SVGPath path = null;
			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch(eventType) {
				case XmlPullParser.START_TAG :
					if(xpp.getName().equals(TAG_SIZE)) {
						int width = Integer.valueOf(xpp.getAttributeValue(null, ATTRIBUTE_X));
						int height = Integer.valueOf(xpp.getAttributeValue(null, ATTRIBUTE_Y));
						if (_width != width || _height != height)
							LogDog.e(TAG, "SVG size mismatch");
					}
					else if(xpp.getName().equals(TAG_PATH)) {
						int pen = Integer.valueOf(xpp.getAttributeValue(null, ATTRIBUTE_PEN));
						path = new SVGPath(pen);
						_paths.add(path);
					}
					else if(xpp.getName().equals(TAG_POINT)) {
						float x = Float.valueOf(xpp.getAttributeValue(null, ATTRIBUTE_X));
						float y = Float.valueOf(xpp.getAttributeValue(null, ATTRIBUTE_Y));
						if (path != null)
							path.addPoint(x, y);
					}
					break;
				case XmlPullParser.END_TAG :
					break;
				}
				eventType = xpp.next();
			}
		} catch (Exception e) {
			LogDog.e(TAG, "Error loading svg:" + e.getMessage());
		}
	}
	
	void addPath(SVGPath newPath) {
		boolean modified = false;
		if(newPath.getPen() == Stationary.ERASER * Stationary.M + Stationary.E_SUPER) {
			// super eraser: erase all intersect paths
			// reversed iterator, so don't need to worry about modified _paths
			for(int i = _paths.size() - 1; i >= 0; --i) {
				SVGPath path = _paths.get(i);
				if(path.getPen() / Stationary.M == Stationary.ERASER)
					continue;
				if(path.intersect(newPath)) {
					_paths.remove(i);
					modified = true;
				}
			}
		}
		else {
			_paths.add(newPath);
			modified = true;
		}
		if (modified)
			saveSVG();
	}
	
	void saveSVG() {
		String svgFile = PDFMarkerApp.instance().getFilesDir().getPath() + "/" + _fileId + "." + _pageId + ".svg";
		XmlSerializer xml = Xml.newSerializer();
		OutputStream out;
		try {
			out = new BufferedOutputStream(new FileOutputStream(svgFile));
			xml.setOutput(out, ENCODING);
			xml.startDocument(ENCODING, true);
			xml.startTag(null, TAG_SIZE);
			xml.attribute(null, ATTRIBUTE_X, String.valueOf(_width));
			xml.attribute(null, ATTRIBUTE_Y, String.valueOf(_height));
			xml.endTag(null, TAG_SIZE);
			for(SVGPath path: _paths)
				path.output(xml);
			xml.endDocument();
			xml.flush();
			out.close();
		} catch (Exception e) {
			LogDog.e(TAG, "Error saving svg:" + e.getMessage());
		}
	}

	void draw(Canvas canvas) {
		for (SVGPath path : _paths) {
			canvas.drawPath(path, Stationary.getPaint(path.getPen()));
		}
	}

	// private
	
	private int						_fileId = -100;
	private int						_pageId = -100;
	private ArrayList<SVGPath>		_paths;

	class SVGPath extends Path {

		public SVGPath(int pen) {
			super();
			_pen = pen;
		}
		
		public int getPen() { return _pen; }
		
		public void output(XmlSerializer xml) throws IllegalArgumentException, IllegalStateException, IOException {
			xml.startTag(null, SVGRecorder.TAG_PATH);
			xml.attribute(null, SVGRecorder.ATTRIBUTE_PEN, String.valueOf(_pen));
			for(PointF p: _points) {
				xml.startTag(null, SVGRecorder.TAG_POINT);
				xml.attribute(null, SVGRecorder.ATTRIBUTE_X, String.valueOf(p.x));
				xml.attribute(null, SVGRecorder.ATTRIBUTE_Y, String.valueOf(p.y));
				xml.endTag(null, SVGRecorder.TAG_POINT);
			}
			xml.endTag(null, SVGRecorder.TAG_PATH);
		}
		
		public void addPoint(float px, float py) {
			if(_points.size() == 0) {
				moveTo(px, py);
				_px = px;
				_py = py;
				if(_px < _minX)
					_minX = _px;
				if(_px > _maxX)
					_maxX = _px;
				if(_py < _minY)
					_minY = _py;
				if(_py > _maxY)
					_maxY = _py;
				_points.add(new PointF(px, py));
			}
			else {
				float dx = Math.abs(px - _px);
				float dy = Math.abs(py - _py);
				if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
					quadTo(_px, _py, (px + _px)/2, (py + _py)/2);
					_px = px;
					_py = py;
					if(_px < _minX)
						_minX = _px;
					if(_px > _maxX)
						_maxX = _px;
					if(_py < _minY)
						_minY = _py;
					if(_py > _maxY)
						_maxY = _py;
					_points.add(new PointF(px, py));
				}
			}
		}
		
		// determine if two paths intersect. this is used to erase a stroke
		public boolean intersect(SVGPath that) {
			// first check if two rectangle area intersects, this can reduce numbers of path to compare
			if(_minX > that._maxX || _minY > that._maxY || _maxX < that._minX || _maxY < that._minY)
				return false;
			for(int i = 0; i < _points.size() - 1; ++i) {
				PointF p1 = _points.get(i);
				PointF p2 = _points.get(i + 1);
				if(Math.max(p1.x, p2.x) < that._minX || Math.min(p1.x, p2.x) > that._maxX
					|| Math.max(p1.y, p2.y) < that._minY || Math.min(p1.y, p2.y) > that._maxY)
					continue;
				for(int j = 0; j < that._points.size() - 1; ++j) {
					PointF p3 = that._points.get(j);
					PointF p4 = that._points.get(j + 1);
					if(Math.max(p3.x, p4.x) < Math.min(p1.x, p2.x) || Math.min(p3.x, p4.x) > Math.max(p1.x, p2.x)
						|| Math.max(p3.y,  p4.y) < Math.min(p1.y, p2.y) || Math.min(p3.y, p4.y) > Math.max(p1.y, p2.y))
						continue;
					if(p2.x == p1.x) {
						if(p1.x > Math.min(p3.x, p4.x) && p1.x < Math.max(p3.x, p4.x))
							return true;
						continue;
					}
					if(p3.x == p4.x) {
						if(p3.x > Math.min(p1.x, p2.x) && p3.x < Math.max(p1.x, p2.x))
							return true;
						continue;
					}
					float a = (p2.y - p1.y) / (p2.x - p1.x);
					float b = p1.y - a * p1.x;
					float c = (p4.y - p3.y) / (p4.x - p3.x);
					float d = p3.y - c * p3.x;
					float x = (d - b) / (a - c);
					if(x > Math.min(p1.x, p2.x) && x < Math.max(p1.x,  p2.x)
						&& x > Math.min(p3.x, p4.x) && x < Math.max(p3.x, p4.x)) {
						return true;
					}
				}
			}
			return false;
		}
		
		static private final float	TOUCH_TOLERANCE = 4; // don't record small moves to avoid point o
		
		private ArrayList<PointF>	_points = new ArrayList<>();
		private final int			_pen;
		private float				_px;
		private float				_py;
		private float				_minX = Float.MAX_VALUE;
		private float				_minY = Float.MAX_VALUE;
		private float				_maxX = 0f;
		private float				_maxY = 0f;
	}
	
}
