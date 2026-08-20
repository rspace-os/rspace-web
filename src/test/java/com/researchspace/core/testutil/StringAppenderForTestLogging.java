package com.researchspace.core.testutil;

import java.io.Serializable;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Test  appender for examining log contents.
 *
 */
public class StringAppenderForTestLogging extends AbstractAppender {
	public String logContents="";

	public StringAppenderForTestLogging(){
		this(StringAppenderForTestLogging.class.getName(), null, null, false, null);
	}

	protected StringAppenderForTestLogging(String name, Filter filter,
			Layout<? extends Serializable> layout,
			boolean ignoreExceptions, Property[] properties) {
		super(name, filter, layout, ignoreExceptions, properties);
	}

	@Override
	public void append(LogEvent event) {
		logContents = logContents + event.getMessage().getFormattedMessage();
	}
	/**
	 * Clears the log of all contents, useful when searching for content
	 *  multiple times in one test
	 */
	public void clearLog(){
		logContents = "";
	}

}
