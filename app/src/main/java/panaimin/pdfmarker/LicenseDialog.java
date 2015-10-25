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
import android.content.Context;
import android.view.View;
import android.webkit.WebView;
import android.widget.CheckBox;

class LicenseDialog extends Dialog implements View.OnClickListener {

	LicenseDialog(Context context) {
		super(context);
		setContentView(R.layout.d_license);
		setTitle(R.string.license);
		WebView wv = (WebView)findViewById(R.id.webView1);
		String url = "file:///android_asset/agpl.html";
		wv.loadUrl(url);
		findViewById(R.id.btn_ok).setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		CheckBox cb_hide = (CheckBox)findViewById(R.id.cb_hide);
		if (cb_hide.isChecked())
			Utility.instance().setPref(PDFMarkerApp.PREF_SHOW_LICENSE, false);
		dismiss();
	}

}
