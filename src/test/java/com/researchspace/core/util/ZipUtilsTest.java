package com.researchspace.core.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.researchspace.core.util.ZipUtils;

public class ZipUtilsTest {

	public @Rule TemporaryFolder tempFolder = new TemporaryFolder();
	private String PREFIX;
	private String EXTRACTED;
	private String ROOTFOLDER;
	private File ROOTFOLDER_FILE;
	File EXTRACTED_FOLDER;
	// contents of file A
	final String EXPECTED_CONTENTS = "this is file A.";

	@Before
	public void setUp() throws Exception {
		PREFIX = tempFolder.getRoot().getAbsolutePath();
		EXTRACTED = PREFIX + "extracted/";
		ROOTFOLDER = "src/test/resources/archives/rootFolder/";
		ROOTFOLDER_FILE = new File(ROOTFOLDER);
		FileUtils.copyDirectoryToDirectory(ROOTFOLDER_FILE, tempFolder.getRoot());
		EXTRACTED_FOLDER = new File(EXTRACTED);
		EXTRACTED_FOLDER.mkdir();
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(EXTRACTED_FOLDER);
	}

	@Test
	public void testFileFilter() throws IOException {
		// get 2 files
		assertEquals(2, FileUtils.listFiles(ROOTFOLDER_FILE, TrueFileFilter.TRUE, null).size());
		Collection dirs = FileUtils.listFilesAndDirs(ROOTFOLDER_FILE, FalseFileFilter.FALSE,
				new WildcardFileFilter("*evel1"));
		// this includes the original direcotry
		assertEquals(2, dirs.size());
	}
	
	@Test
	public void test2() throws IOException {
		String path = "src/test/resources/chem-data-sheets.zip";
		ZipUtils.extractZip(path, PREFIX + "extracted");
		assertEquals(1, EXTRACTED_FOLDER.listFiles().length);
		
	}

	@Test
	public void test() throws IOException {
		ZipUtils.createZip(PREFIX + "archive.zip", new File(ROOTFOLDER));
		ZipUtils.extractZip(PREFIX + "archive.zip", PREFIX + "extracted");
		assertEquals(1, EXTRACTED_FOLDER.listFiles().length);
		File zipRoot = new File(EXTRACTED + "rootFolder");
		// 2 files
		assertEquals(2, FileUtils.listFiles(zipRoot, FileFilterUtils.makeSVNAware(TrueFileFilter.TRUE), null).size());
		String contents = FileUtils.readFileToString(new File(zipRoot, "a.txt"));
		assertEquals(EXPECTED_CONTENTS, contents);
	}
}
