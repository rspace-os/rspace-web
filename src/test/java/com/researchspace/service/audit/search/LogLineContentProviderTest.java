package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import lombok.AllArgsConstructor;
import lombok.Data;

public class LogLineContentProviderTest {

	LogLineContentProvider lineProvider;
	@BeforeEach
	public void setUp() {
		lineProvider = new LogLineContentProviderImpl();
	}

	@AfterEach
	public void tearDown() {
	}
	@Data
	@AllArgsConstructor
	static class Item {
		private File logFile;
	}

	@Test
	public void testCacheSpel() {
		File logFile = new File("AnyFile.txt");
		
		StandardEvaluationContext itemContext = new StandardEvaluationContext(logFile);
		ExpressionParser parser = new SpelExpressionParser();
		//display the value of item.name property
		Expression exp = parser.parseExpression(LogLineContentProviderImpl.cacheKeySpel);
		Assertions.assertEquals("AnyFile.txt", exp.getValue(itemContext, String.class));
		// not match. Don't cache current file
		Expression exp2 = parser.parseExpression(LogLineContentProviderImpl.cacheUnless);
		Assertions.assertTrue(exp2.getValue(itemContext, Boolean.class));
		// matches
		File logFile2 = new File("AnyFile.txt.1");
		StandardEvaluationContext itemContext2 = new StandardEvaluationContext(logFile2);
		Expression exp3 = parser.parseExpression(LogLineContentProviderImpl.cacheUnless);
		Assertions.assertFalse(exp3.getValue(itemContext2, Boolean.class));
	}

}
