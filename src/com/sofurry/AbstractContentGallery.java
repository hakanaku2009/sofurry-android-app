package com.sofurry;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;

import com.sofurry.model.Submission;

public abstract class AbstractContentGallery<T> extends Activity implements ContentController<T> {
	
	private String requestUrl;
	private Map<String, String> requestParameters;
	private ProgressDialog pd;
	protected int numResults;
	protected ArrayList<T> resultList;
	private String errorMessage;
	protected ThumbnailDownloaderThread thumbnailDownloaderThread;
	protected ContentRequestThread<Submission> listRequestThread;
	private GridView galleryView;

	// Separate handler to let android update the view whenever possible
	protected Handler handler = new Handler() {
		@SuppressWarnings("unchecked")
		@Override
		public void handleMessage(Message msg) {
			if (msg.obj != null) {
				resultList = (ArrayList<T>) msg.obj;
				pd.dismiss();
				updateView();
				if (errorMessage != null) {
					closeList();
				}
			} else {
				updateContentList();
			}
		}
	};

	// Get parameters and initiate data fetch thread
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.gallerylayout);
		galleryView = (GridView) findViewById(R.id.galleryview);

		requestUrl = getFetchUrl();
		requestParameters = getFetchParameters();
		pd = ProgressDialog.show(this, "Fetching data...", "Please wait", true, false);
		errorMessage = null;
		listRequestThread = new ContentRequestThread(this, handler, requestUrl, requestParameters);
		listRequestThread.start();
	}

	// Goes back to the main menu
	private void closeList() {
		Bundle bundle = new Bundle();
		if (errorMessage != null) {
			bundle.putString("errorMessage", errorMessage);
		}
		Intent mIntent = new Intent();
		mIntent.putExtras(bundle);
		setResult(RESULT_OK, mIntent);
		finish();
	}

	// Sets the resulting list on the screen
	private void updateView() {
		Log.i("SF AbstractContentList", "updateView called");
		BaseAdapter adapter = getAdapter(this);
		galleryView.setAdapter(adapter);
		// bind a selection listener to the view
		galleryView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView parentView, View childView, int position, long id) {
				setSelectedIndex(position);
			}
		});

	}

	public String parseErrorMessage(String httpResult) {
		try {
			// check for json error message and parse it
			Log.d("List.parseErrorMessage", "response: " + httpResult);
			JSONObject jsonParser;
			jsonParser = new JSONObject(httpResult);
			int messageType = jsonParser.getInt("messageType");
			if (messageType == AppConstants.AJAXTYPE_APIERROR) {
				String error = jsonParser.getString("error");
				Log.d("List.parseErrorMessage", "Error: " + error);
				return error;
			}
		} catch (JSONException e) {
			Log.d("Auth.parseResponse", e.toString());
		}

		return null;

	}

	protected String getFetchUrl() {
		return AppConstants.SITE_URL + AppConstants.SITE_REQUEST_SCRIPT;
	}

	protected abstract void setSelectedIndex(int selectedIndex);

	protected abstract Map<String, String> getFetchParameters();

	protected abstract BaseAdapter getAdapter(Context context);

	protected void updateContentList() {
		updateView();
	}

	
	@Override
	public void finish() {
		super.finish();
		if (thumbnailDownloaderThread != null)
			thumbnailDownloaderThread.stopThread();
	}
	


	
}