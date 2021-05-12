package com.sparrowwallet.sparrow.instance;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

/**
 * The <code>InstanceList</code> class is a logical entry point to the library which extends the functionality of the <code>Instance</code> class.<br>
 * It allows to create an application lock or free it and send and receive messages between first and subsequent instances.<br><br>
 * 
 * This class is intended for passing a list of strings instead of a single string from the subsequent instance to the first instance.<br><br>
 * 
 * <pre>
 *	// unique application ID
 *	String APP_ID = "tk.pratanumandal.unique4j-mlsdvo-20191511-#j.6";
 *	
 *	// create Instance instance
 *	Instance unique = new InstanceList(APP_ID) {
 *	&nbsp;&nbsp;&nbsp;&nbsp;&#64;Override
 *	&nbsp;&nbsp;&nbsp;&nbsp;protected List&lt;String&gt; sendMessageList() {
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;List&lt;String&gt; messageList = new ArrayList&lt;String&gt;();
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;messageList.add("Message 1");
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;messageList.add("Message 2");
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;messageList.add("Message 3");
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;messageList.add("Message 4");
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;return messageList;
 *	&nbsp;&nbsp;&nbsp;&nbsp;}
 *
 *	&nbsp;&nbsp;&nbsp;&nbsp;&#64;Override
 *	&nbsp;&nbsp;&nbsp;&nbsp;protected void receiveMessageList(List&lt;String&gt; messageList) {
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;for (String message : messageList) {
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;System.out.println(message);
 *	&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;}
 *	&nbsp;&nbsp;&nbsp;&nbsp;}
 *	};
 *	
 *	// try to obtain lock
 *	try {
 *	&nbsp;&nbsp;&nbsp;&nbsp;unique.acquireLock();
 *	} catch (InstanceException e) {
 *	&nbsp;&nbsp;&nbsp;&nbsp;e.printStackTrace();
 *	}
 *	
 *	...
 *	
 *	// try to free the lock before exiting program
 *	try {
 *	&nbsp;&nbsp;&nbsp;&nbsp;unique.freeLock();
 *	} catch (InstanceException e) {
 *	&nbsp;&nbsp;&nbsp;&nbsp;e.printStackTrace();
 *	}
 * </pre>
 * 
 * @author Pratanu Mandal
 * @since 1.3
 *
 */
public abstract class InstanceList extends Instance {
	
	/**
	 * Parameterized constructor.<br>
	 * This constructor configures to automatically exit the application for subsequent instances.<br><br>
	 * 
	 * The APP_ID must be as unique as possible.
	 * Avoid generic names like "my_app_id" or "hello_world".<br>
	 * A good strategy is to use the entire package name (group ID + artifact ID) along with some random characters.
	 * 
	 * @param APP_ID Unique string representing the application ID
	 */
	public InstanceList(String APP_ID) {
		super(APP_ID);
	}
	
	/**
	 * Parameterized constructor.<br>
	 * This constructor allows to explicitly specify the exit strategy for subsequent instances.<br><br>
	 * 
	 * The APP_ID must be as unique as possible.
	 * Avoid generic names like "my_app_id" or "hello_world".<br>
	 * A good strategy is to use the entire package name (group ID + artifact ID) along with some random characters.
	 * 
	 * @param APP_ID Unique string representing the application ID
	 * @param AUTO_EXIT If true, automatically exit the application for subsequent instances
	 */
	public InstanceList(String APP_ID, boolean AUTO_EXIT) {
		super(APP_ID, AUTO_EXIT);
	}

	/**
	 * Internal method used in first instance to receive and parse messages from subsequent instances.<br>
	 * The use of this method directly in <code>InstanceList</code> is discouraged. Use <code>receiveMessageList()</code> instead.<br><br>
	 * 
	 * This method is not synchronized.
	 * 
	 * @param message message received by first instance from subsequent instances
	 */
	@Override
	protected final void receiveMessage(String message) {
		if (message == null) {
			receiveMessageList(null);
		}
		else {
			// parse the JSON array string into an array of string arguments
	        JsonArray jsonArgs = JsonParser.parseString(message).getAsJsonArray();
	        
	        List<String> stringArgs = new ArrayList<String>(jsonArgs.size());
	        
	        for (int i = 0; i < jsonArgs.size(); i++) {
	            JsonElement element = jsonArgs.get(i);
	            stringArgs.add(element.getAsString());
	        }
	        
	        // return the parsed string list
	        receiveMessageList(stringArgs);
		}
	}

	/**
	 * Internal method used in subsequent instances to parse and send message to first instance.<br>
	 * The use of this method directly in <code>InstanceList</code> is discouraged. Use <code>sendMessageList()</code> instead.<br><br>
	 * 
	 * It is not recommended to perform blocking (long running) tasks here. Use <code>beforeExit()</code> method instead.<br>
	 * One exception to this rule is if you intend to perform some user interaction before sending the message.<br><br>
	 * 
	 * This method is not synchronized.
	 * 
	 * @return message sent from subsequent instances
	 */
	@Override
	protected final String sendMessage() {
		// convert arguments to JSON array string
        JsonArray jsonArgs = new JsonArray();
        
        List<String> stringArgs = sendMessageList();
        
        if (stringArgs == null) return null;
        
        for (String arg : stringArgs) {
            jsonArgs.add(arg);
        }

        // return the JSON array string
        return jsonArgs.toString();
	}
	
	/**
	 * Method used in first instance to receive list of messages from subsequent instances.<br><br>
	 * 
	 * This method is not synchronized.
	 * 
	 * @param messageList list of messages received by first instance from subsequent instances
	 */
	protected abstract void receiveMessageList(List<String> messageList);
	
	/**
	 * Method used in subsequent instances to send list of messages to first instance.<br><br>
	 * 
	 * It is not recommended to perform blocking (long running) tasks here. Use <code>beforeExit()</code> method instead.<br>
	 * One exception to this rule is if you intend to perform some user interaction before sending the message.<br><br>
	 * 
	 * This method is not synchronized.
	 * 
	 * @return list of messages sent from subsequent instances
	 */
	protected abstract List<String> sendMessageList();
	
}
