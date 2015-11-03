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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;

public class PageActivity extends Activity implements View.OnClickListener {
	
	public static String TAG = "PDFMarker.Activity";

	static final long HIDE_DELAY = 3000;
	
	boolean				_markMode = false;
	StationaryDialog	_stationaryDialog = null;
	int					_fileId;
	PageTurner			_pageTurner;
	ImageButton			_cut = null;
	ImageButton			_mode = null;
	FrameLayout			_container = null;
	Handler				_handler = new Handler();
	Runnable			_cutHider;
	Runnable			_modeHider;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (Utility.instance().getPref(PDFMarkerApp.PREF_FULL_SCREEN, true)) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
			Utility.instance().showToast(R.string.show_mode);
		}
		setContentView(R.layout.a_page);
		_container = (FrameLayout)findViewById(R.id.container);
		_cut = (ImageButton)findViewById(R.id.ib_cut);
		_mode = (ImageButton)findViewById(R.id.ib_mode);
		_cut.setVisibility(View.INVISIBLE);
		_mode.setVisibility(View.INVISIBLE);
		_cut.setOnClickListener(this);
		_mode.setOnClickListener(this);
		_cutHider = new Runnable() {
			@Override
			public void run() {
				_cut.setVisibility(View.INVISIBLE);
			}
		};
		_modeHider = new Runnable() {
			@Override
			public void run() {
				_mode.setVisibility(View.INVISIBLE);
			}
		};
		// try to show the passed in page
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			_fileId = extras.getInt("FILE_ID");
		}
		_pageTurner = (PageTurner)findViewById(R.id.turner);
		// reset the stationary when opening a page
		Stationary.setCurrentStationary(Stationary.PENCIL * Stationary.M + Stationary.P_HB);
	}

	void showCut() {
		if (Utility.instance().getPref(PDFMarkerApp.PREF_FULL_SCREEN, true)) {
			_cut.setVisibility(View.VISIBLE);
			_container.bringChildToFront(_cut);
			_handler.postDelayed(_cutHider, HIDE_DELAY);
		}
	}

	void showMode() {
		if (Utility.instance().getPref(PDFMarkerApp.PREF_FULL_SCREEN, true)) {
			_mode.setVisibility(View.VISIBLE);
			_container.bringChildToFront(_mode);
			_handler.postDelayed(_modeHider, HIDE_DELAY);
		}
	}

	@Override
	public void onClick(View view) {
		if (view == _cut) {
			_pageTurner._current.cutEdge();
			_pageTurner._next.refresh();
			_pageTurner._previous.refresh();
			_cut.setVisibility(View.INVISIBLE);
		} else if (view == _mode) {
			_markMode = !_markMode;
			if(_markMode) {
				if(_stationaryDialog == null) {
					_stationaryDialog = new StationaryDialog(this, _mode);
					_stationaryDialog.setOwnerActivity(this);
				}
				_stationaryDialog.display();
			}
			else
				_mode.setImageResource(R.drawable.ic_hand);
			_mode.setVisibility(View.INVISIBLE);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		Cursor cursor = DB.instance().getFileInfo(_fileId);
		String fileName = cursor.getString(cursor.getColumnIndex(DB.FILES._FILE));
		int pageId = cursor.getInt(cursor.getColumnIndex(DB.FILES._PAGE));
		PDFMaster.instance().openPDF(cursor);
		PDFMaster.instance().gotoPage(pageId);
		cursor.close();
		setTitle(fileName);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		PDFMaster.instance().closePDF();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.page, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent i = new Intent().setClass(this, SettingsActivity.class);
			startActivity(i);
			return true;
		}
		else if(id == R.id.action_mode) {
			_markMode = !_markMode;
			if(_markMode) {
				if(_stationaryDialog == null) {
					_stationaryDialog = new StationaryDialog(this, item);
					_stationaryDialog.setOwnerActivity(this);
				}
				_stationaryDialog.display();
			}
			else
				item.setIcon(R.drawable.ic_hand);
			return true;
		}
		else if(id == R.id.action_cut) {
			_pageTurner._current.cutEdge();
			_pageTurner._next.refresh();
			_pageTurner._previous.refresh();
			return true;
		}
		else if(id == R.id.action_about) {
			Intent i = new Intent().setClass(this, DocActivity.class);
			i.putExtra("DOC", "about");
			startActivity(i);
			return true;
		}
		else if(id == R.id.action_manual) {
			Intent i = new Intent().setClass(this, DocActivity.class);
			i.putExtra("DOC", "manual");
			startActivity(i);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
