package com.researchspace.core.util;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FileOperatorTest {
	
	FileOperator fileOps;
	
	@Rule
	public TemporaryFolder fileStoreRoot = new TemporaryFolder();
	File anyFile;

	@Before
	public void setUp() throws Exception {
		File fileSrc = new File("src/test/resources/exampleDoc.pdf");
		anyFile = File.createTempFile("testFile", ".pdf");
		FileUtils.copyFile(fileSrc, anyFile);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testFileOperator() {
		createFileOperator();
		assertNotNull(fileOps.getFoldOp().getBaseDir());
	}

	@Test
	public void testAddFile() throws IOException {
		createFileOperator();
		URI inserted = fileOps.addFile("a" + File.separator + "b" + File.separator + "c" + File.separator, anyFile,
				"xyz.pdf");
		String manualPath = (fileOps.getFoldOp().getBaseDir() + "/a/b/c/xyz.pdf").replaceAll("\\\\", "/");
		assertThat(inserted.toString(), containsString(manualPath));
	}

	private void createFileOperator() {
		fileOps = new FileOperator();
		fileOps.getFoldOp().setFileStoreRootDir(fileStoreRoot.getRoot());
	}

	@Test
	public void testCopyFile() throws IOException {
		createFileOperator();
		File outfile = fileStoreRoot.newFile("xxxx.pdf");
		fileOps.copyFile(outfile, anyFile, false);
		assertEquals(outfile.length(), anyFile.length());
		assertTrue(outfile.exists());
		assertTrue(anyFile.exists());

		// copy outfile into another file, removing the original
		File outfile2 = fileStoreRoot.newFile("yyyy.pdf");
		fileOps.copyFile(outfile2, outfile, true);
		assertEquals(outfile2.length(), anyFile.length());
		assertTrue(outfile2.exists());
		assertTrue(anyFile.exists());
		assertFalse(outfile.exists());
	}

	@Test
	public void testCopyStream() throws IOException {
		createFileOperator();
		File outfile = fileStoreRoot.newFile();
		FileOutputStream outStream = new FileOutputStream(outfile);
		FileInputStream fis = new FileInputStream(anyFile);
		long expectedCopiedByteCount = fileOps.copyStream(outStream, fis, 0L);
		assertEquals(241366L, expectedCopiedByteCount);
		assertEquals(outfile.length(), anyFile.length());
		assertTrue(outfile.exists());
		assertTrue(anyFile.exists());
		fis.close();
		outStream.close();

		// copy outfile into another file, removing the original
		File outfile2 = fileStoreRoot.newFile("yyyy.pdf");
		FileOutputStream outStream2 = new FileOutputStream(outfile2);
		FileInputStream fis2 = new FileInputStream(outfile);
		fileOps.copyStream(outStream2, fis2, 0L);
		assertEquals(outfile2.length(), anyFile.length());
		assertTrue(outfile2.exists());
		assertTrue(anyFile.exists());
	}

	@Test
	public void testRemoveFile() throws IOException {
		createFileOperator();
		File outfile = fileStoreRoot.newFile();
		assertTrue(outfile.exists());

		// try delete
		fileOps.deleteFile(outfile);
		assertFalse(outfile.exists());
		
		// try delete same path again
		try {
			fileOps.deleteFile(outfile);
			fail("expected exception on subsequent delete");
		} catch (IOException e) {
			boolean expectedMsg = e instanceof FileNotFoundException
					|| e.getMessage().startsWith("File does not exist")
					|| e.getMessage().startsWith("Cannot delete file");
			assertTrue("expected file not exists, but was:" + e.getMessage(), expectedMsg);
		}
	}
	
}
