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

import java.util.ArrayList;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TextView;

public class StationaryDialog extends Dialog  implements OnTabChangeListener {
	
	private static String TAG = "STATIONARY";

	public StationaryDialog(Activity activity, MenuItem menuItem) {
		super(activity);
		_menuItem = menuItem;
		setTitle("Stationary");
		setContentView(R.layout.d_stationary);
		Resources r = PDFMarkerApp.instance().getResources();
		TabHost th = (TabHost)findViewById(R.id.tabhost1);
		th.setup();
		th.addTab(th.newTabSpec("Pencil").setIndicator(null,
			r.getDrawable(R.drawable.ic_pencil)).setContent(R.id.pencil_list));
		th.addTab(th.newTabSpec("Eraser").setIndicator(null,
			r.getDrawable(R.drawable.ic_eraser)).setContent(R.id.eraser_list));
		th.addTab(th.newTabSpec("Highlighter").setIndicator(null,
			r.getDrawable(R.drawable.ic_hl_yellow)).setContent(R.id.highlighter_list));
		th.addTab(th.newTabSpec("ColorPencil").setIndicator(null,
			r.getDrawable(R.drawable.ic_cp_0080ff)).setContent(R.id.colorpencil_list));
		th.setOnTabChangedListener(this);
		_pencils = (ListView)findViewById(R.id.pencil_list);
		_erasers = (ListView)findViewById(R.id.eraser_list);
		_highlighters = (ListView)findViewById(R.id.highlighter_list);
		_colorPencils = (ListView)findViewById(R.id.colorpencil_list);
		// setup all lists
		_pencilsAdapter = new StationaryAdapter();
		_pencilsAdapter.add(new Item(Stationary.PENCIL, Stationary.P_2H, R.drawable.ic_pencil_2h, R.drawable.ic_pencil, "2H"));
		_pencilsAdapter.add(new Item(Stationary.PENCIL, Stationary.P_1H, R.drawable.ic_pencil_1h, R.drawable.ic_pencil, "1H"));
		// set default to HB pencil
		_lastItem = new Item(Stationary.PENCIL, Stationary.P_HB, R.drawable.ic_pencil_hb, R.drawable.ic_pencil, "HB");
		_pencilsAdapter.add(_lastItem);
		_pencilsAdapter.add(new Item(Stationary.PENCIL, Stationary.P_1B, R.drawable.ic_pencil_1b, R.drawable.ic_pencil, "1B"));
		_pencilsAdapter.add(new Item(Stationary.PENCIL, Stationary.P_2B, R.drawable.ic_pencil_2b, R.drawable.ic_pencil, "2B"));
		_pencils.setAdapter(_pencilsAdapter);
		_pencils.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		_pencils.setOnItemClickListener(_pencilsAdapter);
		_erasersAdapter = new StationaryAdapter();
		_erasersAdapter.add(new Item(Stationary.ERASER, Stationary.E_REGULAR, R.drawable.ic_eraser, R.drawable.ic_eraser, "REGULAR"));
		_erasersAdapter.add(new Item(Stationary.ERASER, Stationary.E_SUPER, R.drawable.ic_eraser_super, R.drawable.ic_eraser_super, "SUPER"));
		_erasers.setAdapter(_erasersAdapter);
		_erasers.setOnItemClickListener(_erasersAdapter);
		_highlightersAdapter = new StationaryAdapter();
		_highlightersAdapter.add(new Item(Stationary.HIGH_LIGHTER, Stationary.HL_YELLOW, R.drawable.ic_highlighter_yellow, R.drawable.ic_hl_yellow, "YELLOW"));
		_highlightersAdapter.add(new Item(Stationary.HIGH_LIGHTER, Stationary.HL_GREEN, R.drawable.ic_highlighter_green, R.drawable.ic_hl_green, "GREEN"));
		_highlightersAdapter.add(new Item(Stationary.HIGH_LIGHTER, Stationary.HL_PINK, R.drawable.ic_highlighter_pink, R.drawable.ic_hl_pink, "PINK"));
		_highlighters.setAdapter(_highlightersAdapter);
		_highlighters.setOnItemClickListener(_highlightersAdapter);
		_colorPencilsAdapter = new StationaryAdapter();
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0x0080ff, R.drawable.ic_colorpencil_0080ff, R.drawable.ic_cp_0080ff, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0x80ff80, R.drawable.ic_colorpencil_80ff80, R.drawable.ic_cp_80ff80, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0x4040c0, R.drawable.ic_colorpencil_4040c0, R.drawable.ic_cp_4040c0, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0x408040, R.drawable.ic_colorpencil_408040, R.drawable.ic_cp_408040, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0x804040, R.drawable.ic_colorpencil_804040, R.drawable.ic_cp_804040, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xff0000, R.drawable.ic_colorpencil_ff0000, R.drawable.ic_cp_ff0000, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xff8000, R.drawable.ic_colorpencil_ff8000, R.drawable.ic_cp_ff8000, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xff8080, R.drawable.ic_colorpencil_ff8080, R.drawable.ic_cp_ff8080, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xffc0ff, R.drawable.ic_colorpencil_ffc0ff, R.drawable.ic_cp_ffc0ff, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xffc080, R.drawable.ic_colorpencil_ffc080, R.drawable.ic_cp_ffc080, ""));
		_colorPencilsAdapter.add(new Item(Stationary.COLOR_PENCIL, 0xffff00, R.drawable.ic_colorpencil_ffff00, R.drawable.ic_cp_ffff00, ""));
		_colorPencils.setAdapter(_colorPencilsAdapter);
		_colorPencils.setOnItemClickListener(_colorPencilsAdapter);
	}

	@Override
	public void onTabChanged(String tabName) {
		StationaryAdapter adapter = tabName.equals("Pencil") ? _pencilsAdapter :
			tabName.equals("Eraser") ? _erasersAdapter :
			tabName.equals("Highlighter") ? _highlightersAdapter : _colorPencilsAdapter;
		int position = adapter._selected;
		if(position < 0)
			position = tabName.equals("Pencil") ? 2 : 0;
		_lastItem = (Item)adapter.getItem(position);
		int stationary = _lastItem.getId();
		adapter.notifyDataSetChanged();
		_menuItem.setIcon(_lastItem._menuResourceId);
		Stationary.setCurrentStationary(stationary);
	}
	
	public void display() {
		_menuItem.setIcon(_lastItem._menuResourceId);
		show();
	}

	private MenuItem						_menuItem;
	private ListView						_pencils;
	private StationaryAdapter				_pencilsAdapter;
	private ListView						_erasers;
	private StationaryAdapter				_erasersAdapter;
	private ListView						_highlighters;
	private StationaryAdapter				_highlightersAdapter;
	private ListView						_colorPencils;
	private StationaryAdapter				_colorPencilsAdapter;
	private Item							_lastItem = null;
	
	private class Item {
		public final int		_type;
		public final int		_subType;
		public final String		_label;
		public final int		_resourceId;
		public final int		_menuResourceId;
		public int getId() { return _type * Stationary.M + _subType; }
		public Item(int type, int subType, int resourceId, int menuResourceId, String label) {
			_type = type;
			_subType = subType;
			_resourceId = resourceId;
			_menuResourceId = menuResourceId;
			_label = label;
		}
	}

	private class StationaryAdapter extends BaseAdapter implements OnItemClickListener {
		
		private ArrayList<Item>	_items = new ArrayList<Item>();
		private int				_selected = -1;

		public StationaryAdapter() {}
		public void add(Item item) { _items.add(item); }
		@Override
		public int getCount() { return _items.size(); }
		@Override
		public Object getItem(int position) { return _items.get(position); }
		@Override
		public long getItemId(int position) { return _items.get(position).getId(); }
		
		@Override
		public View getView(int position, View v, ViewGroup parent) {
			if(v == null) {
				LayoutInflater inflater =
					(LayoutInflater)PDFMarkerApp.instance().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(R.layout.r_stationary, parent, false);
			}
			TextView textView = (TextView)v.findViewById(R.id.fileName);
			ImageView imageView = (ImageView)v.findViewById(R.id.indicator);
			Item item = _items.get(position);
			imageView.setImageResource(item._resourceId);
			textView.setText(item._label);
			int id = item._resourceId;
			if(_selected == position) {
				v.setBackgroundColor(Color.CYAN);
			}
			else if(_selected < 0 &&
				(id == R.drawable.ic_pencil_hb ||
					id == R.drawable.ic_eraser ||
					id == R.drawable.ic_highlighter_yellow ||
					id == R.drawable.ic_colorpencil_0080ff)) {
				_selected = position;
				v.setBackgroundColor(Color.CYAN);
			}
			else {
				v.setBackgroundColor(Color.TRANSPARENT);
			}
			return v;
		}

		@Override
		public void onItemClick(AdapterView<?> parent, View v, int pos, long iid) {
			if(pos != _selected) {
				_selected = pos;
				_lastItem = _items.get(pos);
				int stationary = _lastItem.getId();
				Stationary.setCurrentStationary(stationary);
				_menuItem.setIcon(_lastItem._menuResourceId);
				notifyDataSetChanged();
			}
		}
	}
	
}
