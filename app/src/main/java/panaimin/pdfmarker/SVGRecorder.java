package panaimin.pdfmarker;

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
import android.os.AsyncTask;
import android.util.Xml;

public class SVGRecorder {
	
	// static
	
	public static final String		TAG = "SVG";
	
	public static final int			PAGE_PREVIOUS = 0;
	public static final int			PAGE_CURRENT = 1;
	public static final int			PAGE_NEXT = 2;
	
	private static SVGRecorder[]	_instances = new SVGRecorder[3];
	public static int				_currentFileId = -1;
	public static int				_currentPageId = -100;
	private static int				_width;
	private static int				_height;
	
	static public void setSize(int width, int height) {
		_width = width;
		_height = height;
	}
	
	static public void reset() {
		if(_currentFileId >= 0) {
			discardPage(PAGE_PREVIOUS);
			discardPage(PAGE_CURRENT);
			discardPage(PAGE_NEXT);
			_currentFileId = -1;
			_currentPageId = -100;
		}
	}
	
	static public SVGRecorder getInstance(int fileId, int pageId) {
		if(_currentFileId != fileId || pageId != _currentPageId) {
			if(_instances[PAGE_CURRENT] != null)
				_instances[PAGE_CURRENT].saveSVG();
		} else {
			if(_instances[PAGE_CURRENT] == null)
				_instances[PAGE_CURRENT] = new SVGRecorder(fileId, pageId);
			return _instances[PAGE_CURRENT];
		}
		if(_currentFileId != fileId || pageId < _currentPageId - 2 || pageId > _currentPageId + 2) {
			discardPage(PAGE_PREVIOUS);
			discardPage(PAGE_CURRENT);
			discardPage(PAGE_NEXT);
			_instances[PAGE_CURRENT] = new SVGRecorder(fileId, pageId);
		} else if(pageId == _currentPageId - 2) {
			discardPage(PAGE_NEXT);
			discardPage(PAGE_CURRENT);
			_instances[PAGE_NEXT] = _instances[PAGE_PREVIOUS];
			_instances[PAGE_CURRENT] = new SVGRecorder(fileId, pageId);
			_instances[PAGE_PREVIOUS] = null;
		} else if(pageId == _currentPageId - 1) {
			discardPage(PAGE_NEXT);
			_instances[PAGE_NEXT] = _instances[PAGE_CURRENT];
			_instances[PAGE_CURRENT] = _instances[PAGE_PREVIOUS];
			_instances[PAGE_PREVIOUS] = null;
		} else if(pageId == _currentPageId + 1) {
			discardPage(PAGE_PREVIOUS);
			_instances[PAGE_PREVIOUS] = _instances[PAGE_CURRENT];
			_instances[PAGE_CURRENT] = _instances[PAGE_NEXT];
			_instances[PAGE_NEXT] = null;
		} else if(pageId == _currentPageId + 2) {
			discardPage(PAGE_PREVIOUS);
			discardPage(PAGE_CURRENT);
			_instances[PAGE_PREVIOUS] = _instances[PAGE_NEXT];
			_instances[PAGE_CURRENT] = new SVGRecorder(fileId, pageId);
			_instances[PAGE_NEXT] = null;
		}
		_currentFileId = fileId;
		_currentPageId = pageId;
		if(_instances[PAGE_CURRENT] == null)
			_instances[PAGE_CURRENT] = new SVGRecorder(fileId, pageId);
		_instances[PAGE_CURRENT].new AsyncLoad().execute(null, null, null);
		return _instances[PAGE_CURRENT];
	}
	
	static private void discardPage(int offset) {
		if(_instances[offset] != null) {
			_instances[offset] = null;
		}
	}

	static private final String		ENCODING = "UTF-8";
	static private final String		TAG_PATH = "PATH";
	static private final String		TAG_POINT = "POINT";
	static private final String		TAG_SIZE = "SIZE";
	static private final String		ATTRIBUTE_PEN = "PEN";
	static private final String		ATTRIBUTE_X = "X";
	static private final String		ATTRIBUTE_Y = "Y";
	
	// private
	
	private SVGRecorder(int fileId, int pageId) {
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
	
	public void addPath(SVGPath newPath) {
		if(newPath.getPen() == Stationary.ERASER * Stationary.M + Stationary.E_SUPER) {
			// super eraser: erase all intersect paths
			boolean modified = false;
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
			if(modified) {
				_modified = true;
			}
		}
		else {
			_paths.add(newPath);
			_modified = true;
		}
	}
	
	public void saveSVG() {
		if(_modified) {
			_modified = false;
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
	}

	void draw(Canvas canvas) {
		for (SVGPath path : _paths) {
			canvas.drawPath(path, Stationary.getPaint(path.getPen()));
		}
	}

	// private
	
	private boolean					_modified = false;
	private int						_fileId = -100;
	private int						_pageId = -100;
	private ArrayList<SVGPath>		_paths;

	public class SVGPath extends Path {

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
	
	private class AsyncLoad extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			if(_instances[PAGE_NEXT] == null && _currentPageId < PDFMaster.instance().countPages())
				_instances[PAGE_NEXT] = new SVGRecorder(_currentFileId, _currentPageId + 1);
			if(_instances[PAGE_PREVIOUS] == null && _currentPageId > 0)
				_instances[PAGE_PREVIOUS] = new SVGRecorder(_currentFileId, _currentPageId - 1);
			return null;
		}
	}
	
}
