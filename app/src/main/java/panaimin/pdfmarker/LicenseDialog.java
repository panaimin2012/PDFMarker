package panaimin.pdfmarker;

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
