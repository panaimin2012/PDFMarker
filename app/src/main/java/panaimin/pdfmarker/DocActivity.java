package panaimin.pdfmarker;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;

public class DocActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.a_doc);
		WebView webView = (WebView)findViewById(R.id.webView1);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String doc = extras.getString("DOC");
			String url = "file:///android_asset/";
			url += doc;
			if(Locale.getDefault().equals(Locale.CHINA))
				url += ".zh";
			url += ".html";
			webView.loadUrl(url);
			setTitle(doc);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.doc, menu);
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
		return super.onOptionsItemSelected(item);
	}
	
}
