package panaimin.pdfmarker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

public class PageTurner extends FrameLayout implements View.OnTouchListener {

	static final String TAG = "PDFMarker.Turner";
	PageActivity _activity;
	SVGView _current;
	SVGView _previous;
	SVGView _next;
	int _pageId;

	public PageTurner(Context context, AttributeSet attrs) {
		super(context, attrs);
		initView();
	}

	public PageTurner(Context context) {
		super(context);
		initView();
	}

	void initView() {
		_activity = (PageActivity)getContext();
		LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		int fileId = _activity._fileId;
		_pageId = _activity._pageId;
		// current
		_current = new SVGView(_activity);
		addView(_current, params);
		_current.setPage(fileId, _pageId);
		_current.setOnTouchListener(this);
		// previous
		_previous = new SVGView(_activity);
		_previous.setVisibility(View.INVISIBLE);
		addView(_previous, params);
		_previous.setOnTouchListener(this);
		if (_pageId > 0)
			_previous.setPage(fileId, _pageId - 1);
		// next
		_next = new SVGView(_activity);
		_next.setVisibility(View.INVISIBLE);
		_next.setOnTouchListener(this);
		addView(_next, params);
		if (_pageId < PDFMaster.instance().countPages() - 1)
			_next.setPage(fileId, _pageId + 1);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		return false;
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

}
