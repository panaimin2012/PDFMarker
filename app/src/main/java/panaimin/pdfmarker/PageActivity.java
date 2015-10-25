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
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.FrameLayout;

public class PageActivity extends Activity {
	
	public static String TAG = "PDFMarker.Activity";
	
	boolean				_markMode = false;
	StationaryDialog	_stationaryDialog = null;
	int					_fileId;
	PageTurner			_pageTurner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_page);
		// try to show the passed in page
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			_fileId = extras.getInt("FILE_ID");
		}
		_pageTurner = (PageTurner)findViewById(R.id.turner);
		// reset the stationary when opening a page
		Stationary.setCurrentStationary(Stationary.PENCIL * Stationary.M + Stationary.P_HB);
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
