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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Matrix;
import android.provider.BaseColumns;

class DB extends SQLiteOpenHelper {
	
	static String TAG = "PDFMarker.DB";

	static abstract class FILES implements BaseColumns {
		public static final String _T			= "FILES";
		public static final String _PATH		= "_path";
		public static final String _FILE		= "_file";
		public static final String _PAGE		= "_page";
		// remember scale and move of the screen
		// we have only scale and flat move so only need 0,2,4(=0),5 of the matrix
		public static final String _MATRIX0		= "_matrix0";
		public static final String _MATRIX2		= "_matrix2";
		public static final String _MATRIX5		= "_matrix5";
		// _order: integer used to represent last used order
		// when a file is opened, its _order is updated to max(_order) + 1
		public static final String _ORDER		= "_order";
		public static final String _DELETED		= "_deleted";
	}
	
	static DB instance() {
		if(_instance == null)
			_instance = new DB(PDFMarkerApp.instance());
		return _instance;
	}
	
	int insert(String file, String path) {
		// first check if the file is already in database
		String[] columns = new String[] { DB.FILES._ID, DB.FILES._DELETED };
		String where = DB.FILES._FILE + " = ? AND " + DB.FILES._PATH + " = ? ";
		String[] whereArgs = new String[] { file, path };
		Cursor cursor = getReadableDatabase().query(DB.FILES._T, columns, where, whereArgs, null, null, null);
		if(cursor.getCount() > 0) {
			cursor.moveToFirst();
			int id = cursor.getInt(cursor.getColumnIndex(DB.FILES._ID));
			int deleted = cursor.getInt(cursor.getColumnIndex(DB.FILES._DELETED));
			cursor.close();
			if(deleted == 1)
				markDeleted(id, false);
			return id;
		}
		// insert new record
		ContentValues cv = new ContentValues();
		cv.put(DB.FILES._FILE, file);
		cv.put(DB.FILES._PATH, path);
		cv.put(DB.FILES._DELETED, 0);
		int id = (int)getWritableDatabase().insert(DB.FILES._T, null, cv);
		return id;
	}
	
	void markDeleted(int fileId, boolean deleted) {
		String where = " WHERE " + FILES._ID + " = " + fileId;
		int flag = deleted ? 1 : 0;
		String sql = "UPDATE " + FILES._T + " SET " + FILES._DELETED + " =  " + flag + where;
		getWritableDatabase().execSQL(sql);
	}
	
	Cursor getFileInfo(int fileId) {
		String[] columns = new String[] {
			DB.FILES._ID, DB.FILES._FILE, DB.FILES._PATH,  DB.FILES._PAGE,
			DB.FILES._MATRIX0, DB.FILES._MATRIX2, DB.FILES._MATRIX5 };
		String where = "" + DB.FILES._ID + " = " + fileId;
		Cursor cursor = getReadableDatabase().query(DB.FILES._T, columns, where, null, null, null, null);
		cursor.moveToFirst();
		return cursor;
	}
	
	Cursor getFiles() {
		String[] cs = new String[] { FILES._ID, FILES._PATH, FILES._FILE, FILES._PAGE, FILES._ORDER };
		String where = FILES._DELETED + " = 0 ";
		String orderBy = FILES._ORDER + " DESC ";
		Cursor cursor = getReadableDatabase().query(FILES._T, cs, where, null, null, null, orderBy);
		cursor.moveToFirst();
		return cursor;
	}
	
	void onFileOpen(int fileId) {
		String[] cs = new String[] { "MAX(" + FILES._ORDER + ")" };
		Cursor cursor = getReadableDatabase().query(FILES._T, cs, null, null, null, null, null);
		cursor.moveToFirst();
		int maxOrder = cursor.getInt(0) + 1;
		String s = "UPDATE " + FILES._T + " SET " + FILES._ORDER + " = " + maxOrder + " WHERE " + FILES._ID + " = " + fileId;
		getWritableDatabase().execSQL(s);
	}
	
	void setLastPage(int fileId, int page) {
		ContentValues cv = new ContentValues();
		cv.put(FILES._PAGE, page);
		String where = FILES._ID + " = " + fileId;
		getWritableDatabase().update(FILES._T, cv, where, null);
	}
	
	void cutEdge(int fileId, Matrix matrix) {
		ContentValues cv = new ContentValues();
		float[] mv = new float[9];
		if(matrix != null)
			matrix.getValues(mv);
		cv.put(FILES._MATRIX0, mv[0]);
		cv.put(FILES._MATRIX2, mv[2]);
		cv.put(FILES._MATRIX5, mv[5]);
		String where = FILES._ID + " = " + fileId;
		getWritableDatabase().update(FILES._T, cv, where, null);
	}
	
	// private
	
	static private DB				_instance	= null;
	static private final int		_version 	= 2;
	
	private DB(Context context) {
		super(context, "DB", null, _version);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String s = "CREATE TABLE " + FILES._T + " ( "
			+ FILES._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ FILES._PATH + " TEXT, "
			+ FILES._FILE + " TEXT, "
			+ FILES._PAGE + " INTEGER, "
			+ FILES._MATRIX0 + " REAL DEFAULT 0, "
			+ FILES._MATRIX2 + " REAL DEFAULT 0, "
			+ FILES._MATRIX5 + " REAL DEFAULT 0, "
			+ FILES._ORDER + " INTEGER DEFAULT 0, "
			+ FILES._DELETED + " INTEGER DEFAULT 0 ) ";
		db.execSQL(s);
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if(oldVersion == 1 && newVersion == 2) {
			String s = "ALTER TABLE " + FILES._T + " ADD " + FILES._DELETED + " INTEGER DEFAULT 0 ";
			LogDog.i(TAG, s);
			db.execSQL(s);
		}
		else {
			String s = "DROP TABLE IF EXISTS " + FILES._T;
			db.execSQL(s);
			onCreate(db);
		}
	}

}
