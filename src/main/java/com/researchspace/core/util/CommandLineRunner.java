package com.researchspace.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.nio.charset.StandardCharsets;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for running command line external processes; delegates to
 * apache.commons.exec.
 *
 */
public class CommandLineRunner {

	static Logger logger = LoggerFactory.getLogger(CommandLineRunner.class);

	/**
	 * Most basic command line runner that assumes that command line has no
	 * characters requiring escaping and will return a string. <br/>
	 * Any exceptions are caught and logged. <br/>
	 * Times out after 5 seconds, so is for short-running commands only.
	 * 
	 * @param commandline
	 * @return string output or empty string if error or no output
	 */
	public String runCommandReturningOutput(String commandline) {
		return runCommandReturningOutput(commandline, 5000);
	}

	public String runCommandReturningOutput(String commandline, long timeout) {
		CommandLine cmdLine = CommandLine.parse(commandline);
		return doExecute(commandline, cmdLine, timeout);
	}

	/**
	 * 
	 * @param commandline
	 *            to execute
	 * @param timeout
	 *            in millis
	 * @return the return code of the invoked process
	 */
	public int runCommandReturningExitStatus(String commandline, long timeout) {
		CommandLine cmdLine = CommandLine.parse(commandline);
		return doExecuteReturnCode(commandline, cmdLine, timeout);
	}

	private int doExecuteReturnCode(String commandline, CommandLine cmdLine, long timeout) {
		DefaultExecutor executor = setupExecutor(timeout);

		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
			executor.setStreamHandler(streamHandler);
			int exitValue = executor.execute(cmdLine);
			return exitValue;
		} catch (IOException e) {
			logger.warn("Error executing command line '{}' - {}", commandline, e.getMessage());
			return -1;
		}
	}

	private String doExecute(String commandline, CommandLine cmdLine, long timeout) {
		DefaultExecutor executor = setupExecutor(timeout);
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
			executor.setStreamHandler(streamHandler);
			executor.execute(cmdLine);
			return outputStream.toString(StandardCharsets.UTF_8);
		} catch (IOException e) {
			logger.warn(" Error executing command line {} - {}", commandline, e.getMessage());
			return "";
		}
	}

	private DefaultExecutor setupExecutor(long timeout) {
		DefaultExecutor executor = new DefaultExecutor();
		ExecuteWatchdog watchdog = new ExecuteWatchdog(timeout);
		executor.setWatchdog(watchdog);
		return executor;
	}

	/**
	 * Runs a command where some parts of commandline might need escaping
	 * 
	 * @param commandElements
	 * @return
	 */
	public String runCommandReturningOutput(String... commandElements) {
		CommandLine cmdLine = new CommandLine(commandElements[0]);
		if (commandElements.length > 1) {
			for (int i = 1; i < commandElements.length; i++) {
				cmdLine.addArgument(commandElements[i]);
			}
		}
		return doExecute(StringUtils.join(commandElements), cmdLine, 5000);
	}
}
