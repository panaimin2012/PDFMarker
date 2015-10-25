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

import android.app.Dialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class GotoDialog extends Dialog implements OnClickListener {

	public GotoDialog(PageActivity activity) {
		super(activity);
		_activity = activity;
		setContentView(R.layout.d_goto);
		_pageNumber = (TextView)findViewById(R.id.pageNumber);
		Button btn = (Button)findViewById(R.id.button1);
		btn.setOnClickListener(this);
		TextView maxPage = (TextView)findViewById(R.id.max);
		maxPage.setText(String.valueOf(PDFMaster.instance().countPages()));
	}
	
	public void setPage(int pageNumber) {
		_pageNumber.setText(String.valueOf(pageNumber));
	}

	@Override
	public void onClick(View v) {
		int n = Integer.parseInt(_pageNumber.getText().toString());
		if (n < 1 || n > PDFMaster.instance().countPages()) {
			Utility.instance().showToast(R.string.msg_wrong_page);
			return;
		}
		_activity._pageTurner.turnToPage(n - 1);
		dismiss();
	}
	
	private PageActivity	_activity;
	private TextView		_pageNumber;

}
