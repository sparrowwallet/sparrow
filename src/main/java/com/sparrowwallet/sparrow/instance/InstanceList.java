package com.sparrowwallet.sparrow.instance;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public abstract class InstanceList extends Instance {
	
	public InstanceList(String applicationId) {
		super(applicationId);
	}
	
	public InstanceList(String applicationId, boolean autoExit) {
		super(applicationId, autoExit);
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
		if(message == null) {
			receiveMessageList(null);
		} else {
			// parse the JSON array string into an array of string arguments
	        JsonArray jsonArgs = JsonParser.parseString(message).getAsJsonArray();
	        
	        List<String> stringArgs = new ArrayList<String>(jsonArgs.size());
	        
	        for(int i = 0; i < jsonArgs.size(); i++) {
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
        if(stringArgs == null) {
            return null;
        }
        
        for(String arg : stringArgs) {
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
