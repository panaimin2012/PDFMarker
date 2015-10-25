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

import android.app.Application;
import android.widget.Toast;

public class PDFMarkerApp extends Application {

	private static PDFMarkerApp		_instance;

	static final String PREF_SHOW_LICENSE = "SHOW_LICENSE";

	@Override
	public void onCreate() {
		super.onCreate();
		_instance = this;
		Utility.instance(_instance);
		LogDog.instance().init(this);
	}
	
	@Override
	public void onTerminate() {
		LogDog.instance().close();
		super.onTerminate();
	}

	public static PDFMarkerApp instance() {
		return _instance;
	}
	
}
