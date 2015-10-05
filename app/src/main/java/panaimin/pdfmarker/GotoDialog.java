package panaimin.pdfmarker;

import android.app.Dialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class GotoDialog extends Dialog implements OnClickListener {

	public GotoDialog(PageActivity activity) {
		super(activity);
		_activity = activity;
		setContentView(R.layout.layout_goto);
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
		if(n > 0 && n <= PDFMaster.instance().countPages()) {
			_activity.turnToPage(n - 1);
		}
		dismiss();
	}
	
	private PageActivity	_activity;
	private TextView		_pageNumber;

}
