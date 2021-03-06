package com.sofurry.activities;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Message;
import android.os.Vibrator;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sofurry.AppConstants;
import com.sofurry.R;
import com.sofurry.base.classes.ActivityWithRequests;
import com.sofurry.mobileapi.ChatApiFactory;
import com.sofurry.mobileapi.core.AuthenticationHandler;
import com.sofurry.mobileapi.core.Request;
import com.sofurry.requests.AndroidRequestWrapper;
import com.sofurry.requests.DataCall;
import com.sofurry.util.ErrorHandler;

/**
 * @author --
 * 
 * This needs to be fixed, as soon as the new chat api is in place. Right now this is not
 * functional.
 *
 */
public class ChatActivity extends ActivityWithRequests {
    
	private ScrollView scrollView;
    private TextView chatView;
    private TextView roomView;
    private EditText chatEntry;
    private Button sendButton;
	
    private static int chatSequence = -1;
	private static int roomId = -1;
	
	private static int roomIds[] = null;
	private static String roomNames[] = null;
	private String userNames[] = null;
	private static CharSequence textSave = null;
	
	protected ChatPollThread chatPollThread;
	protected ChatSendThread chatSendThread;
	String requestUrl = AppConstants.SITE_URL + AppConstants.SITE_REQUEST_SCRIPT;
	protected LinkedBlockingQueue<String> chatSendQueue;
	
	//private static String MESSAGETYPE_MESSAGE = "message";
	private static String MESSAGETYPE_WHISPER = "whisper";
		
//	@Override
//	public void onOther(int id, Object obj) throws Exception {
//		// If the object is text, it will be handled by the texthandler
//		if (String.class.isAssignableFrom(obj.getClass())) {
//			addTextToChatLog((String)obj);
//		} else
//		    super.onOther(id,obj);
//	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onPause()
	 * Saves some data in static fields, so the data can be restored after the orientation change
	 */
	@Override
	protected void onPause() {
		killThreads();
		textSave = chatView.getText();
		super.onPause();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 * Setzt daten 
	 */
	@Override
	protected void onResume() {
		if (textSave != null)
		  chatView.setText(textSave);
		textSave = null;
		startThreads();
		super.onResume();
	}


	/**
	 * Displays errors as they occur
	 * @param e
	 */
	public void showError(Exception e) {
		ErrorHandler.showError(this, e);
	}

	/**
	 * Forwards the text returned by either the poll command or the send text command to the chatlog
	 * @param obj
	 */
	public void forwardTextToChatLog(JSONObject obj) {
		DataCall toCall = new DataCall() {
			public void call() throws Exception {
				addTextToChatLog((JSONObject)arg1);
			}
		};
		toCall.arg1 = obj;

		// Post the callback into the request handler
		Message msg = new Message();
		msg.obj = toCall;
		requesthandler.postMessage(msg);
	}
	
	/**
	 * Adds text that was received from the AJAX Api, to the displayed chatlog
	 * @param str
	 */
	private void addTextToChatLog(JSONObject messages) throws JSONException {
		JSONArray msgitems = messages.optJSONArray("messages");

		int numResults = msgitems.length();
		for (int i = 0; i < numResults; i++) {
			JSONObject message = msgitems.getJSONObject(i);
			
			// Extract the json objects
			String id = message.getString("id");
			String fromUserName = message.getString("fromname");
			String type = message.getString("msgtype"); //can be message or whisper

			String text = message.getString("text");
			if (Integer.parseInt(id) > chatSequence) {
				chatSequence = Integer.parseInt(id);
			}
			chatView.append(fromUserName);
			chatView.append(": ");
            SpannableString sstr = colorText(text, type);
            chatView.append(sstr);
            Linkify.addLinks(chatView, Linkify.ALL);
            chatView.append("\n");
		}

		scrollView.scrollTo(0, chatView.getHeight());
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.chatlayout);
		chatView = (TextView) findViewById(R.id.chatview);
		roomView = (TextView) findViewById(R.id.roomtextview);
        scrollView = (ScrollView) findViewById(R.id.scrollview);
        chatEntry = (EditText) findViewById(R.id.chatentry);
        sendButton = (Button) findViewById(R.id.send);

        /* Create send button callback */
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                onSend();
            }
        });

        /* Create 'return' key callback */
        chatEntry.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View view, int keyCode, KeyEvent event) {
                /* 'Enter' pressed */
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    onSend();
                    return true;
                } else {
                    return false;
                }
            }
        });
        
        if (savedInstanceState == null) {
	        if (roomId == -1) // This might be called for the first time in the app's lifecycle
	          getRoomList(); // Get Room list will populate the room list, and start the room selection
	        else
	        {  // The data is already available
	        	try {
	     		   changeRoom(roomIdToIdx(roomId));
				} catch (Exception e) {
				   onError(e);
				}
			}
        }
	}

	/**
	 * Looks up the RoomID in the list of roomid's
	 * @param roomid
	 * The roomid to look up
	 * @return
	 * Returns the index to the roomID's
	 * @throws Exception
	 */
	private static int roomIdToIdx(int roomid) throws Exception {
        int idx = -1;
        for (int i = 0; i < roomIds.length; i++)
     	   if (roomIds[i] == roomId)
     		   idx = i;
        if (idx == -1) throw new Exception("RoomID was not found in RoomID list");
        return idx;
	}

    /**
     * Kills all the threads for this chat session
     */
    private void killThreads() {
		if (chatPollThread != null)
			chatPollThread.stopThread();
		if (chatSendThread != null)
			chatSendThread.stopThread();
		chatPollThread = null;
		chatSendThread = null;
    }
    
    /**
     * Starts all the threads for this chat session
     */
    private void startThreads() {
    	if (roomId == -1) return; // If no room is selected yet, we do not need to bother to poll for messages
		// Start the polling for our new room
		chatPollThread = new ChatPollThread(ChatActivity.roomId);
		chatPollThread.start();
		chatSendQueue = new LinkedBlockingQueue<String>();
		chatSendThread = new ChatSendThread(ChatActivity.roomId);
		chatSendThread.start();
    }

	
	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 * 
	 * Creates the Context Menu for this Activity.
	 */
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);
		menu.add(0,AppConstants.MENU_CHGROOM,0,"Rooms");
		menu.add(0,AppConstants.MENU_USERS,0,"Users");
		return result;
	}
	
	/* (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 * 
	 * Handles feedback from the context menu
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case AppConstants.MENU_CHGROOM:
			roomSelect();
			return true;
		case AppConstants.MENU_USERS:
			getUserList();
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	
	/**
	 * Requests the room list from the AJAX api, and will call the populateRoomList callback
	 */
	private void getRoomList() {
		// Do we need to fetch the room list?
		if ((roomIds == null) || (roomIds.length == 0)) {
			pbh.showProgressDialog("Fetching rooms");
			
			Request getRooms = ChatApiFactory.createRoomList();
            AndroidRequestWrapper arw = new AndroidRequestWrapper(requesthandler, getRooms);
            arw.exec(new DataCall() {public void call() throws Exception {populateRoomList((JSONObject)arg1);}});
			
//			AjaxRequest getRooms = new AjaxRequest();
//			getRooms.addParameter("f", "chatrooms");
//			getRooms.setRequestID(AppConstants.REQUEST_ID_ROOMLIST); // Mark this request, so the return value handler knows what to do with the result
//			getRooms.execute(requesthandler);
		} else { // No we already have it, call dialog directly
			roomSelect();
		}
	}

	/**
	 * Populates the roomlist, and starts the room selection dialog
	 * @param obj
	 */
	private void populateRoomList(JSONObject obj) {
		try {
			ArrayList<JSONObject> collect = new ArrayList<JSONObject>();
			
			// Parse to data location
			JSONObject data = obj.getJSONObject("data");

			JSONObject inbetween = data.getJSONObject("list"); 
			
			JSONArray list = inbetween.toJSONArray(inbetween.names());
			
			int cntlist = data.getInt("count");
			
			for (int j = 0; j < cntlist; j++) {
				JSONObject listitem = list.getJSONObject(j);

				inbetween = listitem.getJSONObject("rooms");
				
				JSONArray rooms = inbetween.toJSONArray(inbetween.names());
				
				int cnt = rooms.length();
				roomNames = new String[cnt];
				roomIds = new int[cnt];
				for (int i = 0; i < cnt; i++) {
					JSONObject item = rooms.getJSONObject(i);
					collect.add(item);
				}
			}
			
			int cnt = collect.size();
			int i = 0;
			roomNames = new String[cnt];
			roomIds = new int[cnt];
			for (JSONObject item:collect) {
				Log.d(AppConstants.TAG_STRING, "Item: " + item.getString("name") + " " + item.getString("id"));
				roomNames[i] = Html.fromHtml(item.getString("name")).toString();
				roomIds[i] = item.getInt("id");
				i++;
			}

			roomSelect();
			
		} catch (Exception e) {
			showError(e);
		} finally {
			pbh.hideProgressDialog();
		}
	}
	
	/**
	 * Shows a Room selection dialog to the user, using the global roomTitles and roomIds variables
	 * The user will be able to change to the desired room by clicking on it.
	 */
	private void roomSelect() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Choose your Room:");
		builder.setItems(roomNames, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		    	changeRoom(item);
		    }
		});
		AlertDialog roomchooser = builder.create();
		roomchooser.show();
	}
	
	/**
	 * Changes the room, according to the provided index (sets the id in roomIDs[])
	 * @param idx
	 * The index in the two arrays roomNames and roomIDs
	 */
	private void changeRoom(int idx) {
		
		killThreads(); // For recalls
		// Leave the old room
		if (roomId != -1) {
			Request leaveRoom = ChatApiFactory.createPart(roomId);
	        AndroidRequestWrapper arw = new AndroidRequestWrapper(requesthandler, leaveRoom);
	        arw.exec(new DataCall() {public void call() throws Exception {roomLeft((JSONObject)arg1);}});
		}
        // Update the information for the new room
		roomId = roomIds[idx];
		chatSequence = -1;
		chatView.setText(""); // Clear chat window
		roomView.setText("Changing room...");

		// Join the room
		Request joinRoom = ChatApiFactory.createJoin(roomId);
        AndroidRequestWrapper arw2 = new AndroidRequestWrapper(requesthandler, joinRoom);
        arw2.exec(new DataCall() {public void call() throws Exception {changeRoomStartPolling((JSONObject)arg1);}});
	}
	
	/**
	 * Joins the room once leaving the room is complete
	 */
	private void roomLeft(JSONObject leave) {
		try {
			checkReplyForError(leave);
		} catch (Exception e) {
	    	Toast.makeText(getApplicationContext(), "Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
	
	
	/**
	 * Checks a JSON Replay for an error message
	 * @param reply
	 * @throws Exception
	 */
	private void checkReplyForError(JSONObject reply) throws Exception {
		String message = reply.optString("message","unknown");
		String type = reply.optString("type","none");
		// Check for error.
		if ("error".equals(type))
		  throw new Exception(message);
	}
	
	/**
	 * Is called once the join command returns
	 * @param poll
	 */
	private void changeRoomStartPolling(JSONObject poll) {
		try {
			checkReplyForError(poll);
			
			startThreads();

			roomView.setText("Room:" + roomNames[roomIdToIdx(roomId)]);
			//+ "("+roomIdToIdx(roomId)+")"
	    	Toast.makeText(getApplicationContext(), "Joined:" + roomNames[roomIdToIdx(roomId)] , Toast.LENGTH_SHORT).show();
		} catch (Exception e) {
	    	Toast.makeText(getApplicationContext(), "Error:" + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Requests a list of currently online users in that room
	 * Since this list is subject to change, it gets updated every time, and destroyed once the menu is not used anymore
	 */
	private void getUserList() {
		pbh.showProgressDialog("Fetching users");
		
		Request getUsers = ChatApiFactory.createUserList(roomId);
        AndroidRequestWrapper arw = new AndroidRequestWrapper(requesthandler, getUsers);
        arw.exec(new DataCall() {public void call() throws Exception {populateUserList((JSONObject)arg1);}});

//		AjaxRequest getRooms = new AjaxRequest();
//		getRooms.addParameter("f", "onlineUsers");
//		getRooms.addParameter("roomid", "" + ChatActivity.roomId);
//		getRooms.setRequestID(AppConstants.REQUEST_ID_USERLIST); // Mark this request, so the return value handler knows what to do with the result
//		getRooms.execute(requesthandler);
	}

	/**
	 * Populates the userlist with the data returned from the API.
	 * @param obj
	 */
	private void populateUserList(JSONObject obj) {
		try {
			checkReplyForError(obj);
			
			JSONArray items = obj.getJSONArray("users");
			int cnt = items.length();
			userNames = new String[cnt];
			for (int i = 0; i < cnt; i++) {
				JSONObject item = items.getJSONObject(i);
				userNames[i] = Html.fromHtml(item.getString("useralias")).toString();
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Select user:");
			builder.setItems(userNames, new DialogInterface.OnClickListener() {
			    public void onClick(DialogInterface dialog, int item) {
			    	addUserName(item);
			    }
			});
			AlertDialog userchooser = builder.create();
			userchooser.show();
			
		} catch (Exception e) {
			showError(e);
		} finally {
			pbh.hideProgressDialog();
		}
	}
	
	/**
	 * Adds a username from the user array to the textfield
	 * @param idx
	 * The index to the username.
	 */
	private void addUserName(int idx) {
		if ((chatEntry.length() > 0) && (!chatEntry.getText().toString().endsWith(" ")))
		  chatEntry.getText().append(" ");
		chatEntry.getText().append(userNames[idx]);
		userNames = null; // Since the list will be recreated, we don't need this anymore.
	}
	
//	/**
//	 * Polls the Server for new messages to display
//	 * @param roomId
//	 * The ID of the currently selected room
//	 * @return
//	 */
//	protected String pollChat(int roomId) {
//		//Send chat poll request, return result
//		return ChatApiFactory.createChatFetch(chatSequence, roomId).toString();
//		
////		AjaxRequest req = new AjaxRequest(requestUrl);
////		
////		//Map<String, String> requestParameters = new HashMap<String, String>();
////		req.addParameter("f", "chatfetch");
////		req.addParameter("lastid", ""+chatSequence);
////		req.addParameter("roomid", ""+roomId);
////
////		try {
////			String httpResult = RequestThread.authenticadedHTTPRequest(req);
////			RequestThread.parseErrorMessage(new JSONObject(httpResult));
////			return httpResult;
////
////		} catch (Exception e) {
////			e.printStackTrace();
////		}
//
//		return null;
//	}

	//This is the main message polling thread. This is necessary because we can't block the UI with our http requests 
	private class ChatPollThread extends Thread {
		boolean keepRunning = true;
		int roomId;
		
		// Set saveUserAvatar to true to save the returned thumbnail as the submission's user avatar
		public ChatPollThread(int roomId) {
			this.roomId = roomId;
		}

		public void stopThread() {
			keepRunning = false;
		}

		

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 * Polls for text to appear in the selected chatroom
		 */
		public void run() {
			while (keepRunning) {
				try {
					// Poll for text
					Request pollMessages = ChatApiFactory.createChatBacklog(chatSequence, roomId);
					JSONObject result = pollMessages.execute();
					
					Log.d("chat",result.toString());
					// Forward the returned text into the chatlog
					forwardTextToChatLog(result);

					//String result = ChatApiFactory.createChatFetch(chatSequence, roomId).toString();
//					if (result != null) {
//						requesthandler.postMessage(result);
//					}
					
					Thread.sleep(3000);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}

	//This is a thread for sending chat messages. It will work off messages off a queue 
	private class ChatSendThread extends Thread {
		boolean keepRunning = true;
		int roomId;
		
		// Set saveUserAvatar to true to save the returned thumbnail as the submission's user avatar
		public ChatSendThread(int roomId) {
			this.roomId = roomId;
		}

		public void stopThread() {
			keepRunning = false;
		}

		/* (non-Javadoc)
		 * @see java.lang.Thread#run()
		 * 
		 * Checks the chat queue in intervals and sends messages to the chat handler
		 */
		public void run() {
			while (keepRunning) {
				try {
					Thread.sleep(500);
					String message = chatSendQueue.poll();
					if (message == null) continue; 

					// Send the text into the channel
					JSONObject reply = ChatApiFactory.createSendMessage(message, roomId).execute();
					
					// Forward text to chatlog
					forwardTextToChatLog(reply);

					
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

	}

	    /* called to colorize messages */
    private SpannableString colorText(CharSequence text, String type) {
        Spanned s = Html.fromHtml(text.toString());
        SpannableString str = new SpannableString(s.toString());
        int color;
        /* topic color */
        if (text.toString().startsWith("*** Topic is: "))
            color = Color.YELLOW;
        /* nicks list color */
        else if (text.toString().startsWith("*** Nicks are: "))
            color = Color.GREEN;
        /* action color */
        else if (text.toString().startsWith("***"))
            color = Color.LTGRAY;
        /* cite color */
        else if (!text.toString().startsWith(AuthenticationHandler.getUsername()) && text.toString().toLowerCase().contains(AuthenticationHandler.getUsername().toLowerCase())) {
            color = Color.CYAN;
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            v.vibrate(200);
        }
        /* privmsg color */
        else if (text.toString().startsWith("<"))
        {
            color = Color.WHITE;
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            v.vibrate(200);
        }
        /* default color */
        else if (type.equalsIgnoreCase(MESSAGETYPE_WHISPER)) {
        	color = Color.BLACK;
            str.setSpan(new BackgroundColorSpan(Color.YELLOW), 0, s.toString().length(), 0);
        } else {
            color = Color.WHITE;
        }
        
        str.setSpan(new ForegroundColorSpan(color), 0, s.toString().length(), 0);
        
        
        return str;
    }

    private void onSend() {
    	Log.i(AppConstants.TAG_STRING, "Chat: onSend() called");
    	chatSendQueue.add(chatEntry.getText().toString());
    	chatEntry.setText("");
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
        	finish();
            //return true;
        }

        return super.onKeyDown(keyCode, event);
    }

	@Override
	public void finish() {
		super.finish();
		killThreads();
		roomId = -1;
		roomIds = null;
		roomNames = null;
	}

}
