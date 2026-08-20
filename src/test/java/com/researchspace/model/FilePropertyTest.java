package com.researchspace.model;

import static com.researchspace.model.record.TestFactory.createAnyFileStoreRoot;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.record.TestFactory;



public class FilePropertyTest {
	
	User user;
	FileStoreRoot fsroot;
	File anyFile;

	@BeforeEach
	 void setUp() throws Exception {
		fsroot = createAnyFileStoreRoot();
		user = createAnyUser("any");
		anyFile = RSpaceModelTestUtils.getAnyImage();
	}

	@AfterEach
	 void tearDown() throws Exception {
	}

	@Test
	 void testGetAbsolutePathUri() {
		FileProperty fp = TestFactory.createAFileProperty(anyFile, user, fsroot);
		assertNotNull(fp.getRelPath());
		fp.generateURIFromProperties(fsroot.getFileStoreRootDir());
		String expected = (fsroot.getFileStoreRoot()+fp.getRelPath()).replaceAll("\\\\", "/");
		assertEquals(expected, fp.getAbsolutePathUri());
	}
	
	@Test
	 void testFPandRootAreNotExternalByDefault() {
		FileProperty fp = TestFactory.createAFileProperty(anyFile, user, fsroot);
		assertFalse(fp.isExternal());
		assertFalse(fp.getRoot().isExternal());
	}
	
	@Test
	void builder () {
		FileProperty fp = FileProperty.builder().fileCategory("images").fileGroup("labgroup1")
				.fileOwner("me").fileUser("other").fileVersion("1").build();
		assertNotNull(fp.getCreateDate());
	}
}
