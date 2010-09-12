package com.sofurry;

import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.BaseAdapter;

import com.sofurry.requests.AjaxRequest;

public interface IManagedActivity<T> {

	public abstract void onCreate(Bundle savedInstanceState);
	
	public abstract void plugInAdapter();

	public abstract void updateView();

	public abstract boolean onCreateOptionsMenu(Menu menu);

	public abstract boolean onOptionsItemSelected(MenuItem item);

	public abstract void setSelectedIndex(int selectedIndex);

	public abstract AjaxRequest getFetchParameters(int page, int source);

	public abstract BaseAdapter getAdapter(Context context);

	public abstract void parseResponse(JSONObject obj) throws Exception;

	public abstract void finish();
	
	public abstract void resetViewSourceExtra(int newViewSource);
	
	
}