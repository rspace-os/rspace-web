package com.researchspace.model.views;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;

public class RecordCopyResultTest {

	RecordCopyResult result;
	Folder anyFolder;
	User user = TestFactory.createAnyUser("user");
	@BeforeEach
	public void setUp() throws Exception {
		anyFolder = TestFactory.createAFolder("any", user);
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws InterruptedException {
		result= new RecordCopyResult(anyFolder, true);
		StructuredDocument sdOrig = TestFactory.createAnySD();
		Thread.sleep(1);// ensure distinct
		StructuredDocument copy = TestFactory.createAnySD();
		assertNull(result.getUniqueCopy());
		result.add(sdOrig, copy);
		assertEquals(copy,result.getUniqueCopy());
	}

	

}
