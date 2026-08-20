package com.researchspace.core.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Handler that can be used to collect and browse messages 
 * passed to java.util.logging.Logger.
 */
public class MessagesLoggingHandler extends Handler {

	List<String> messages = new ArrayList<>();
	
	@Override
	public void publish(LogRecord record) {
		messages.add(record.getMessage());
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() throws SecurityException {
	}

	public List<String> getMessages() {
		return messages;
	}
}
