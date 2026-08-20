package com.researchspace.model.netfiles;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NfsFileSystemInfoTest{

	@Test
	public void testCopyConstructorUserRequiresRootDirs() {

		String testClientOptionsString = "USER_DIRS_REQUIRED=true";
		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientOptions(testClientOptionsString);
		fileSystem.setClientType(NfsClientType.SFTP);
		fileSystem.setId(1L);
		fileSystem.setUrl("url");
		fileSystem.setName("name");
		fileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
		NfsFileSystemInfo testee = new NfsFileSystemInfo(fileSystem);
		assertEquals("true",testee.getOptions().get("USER_DIRS_REQUIRED"));
		assertEquals("SFTP",testee.getClientType());
		assertEquals("PASSWORD",testee.getAuthType());
		assertTrue(1L == testee.getId());
		assertEquals("url",testee.getUrl());
		assertEquals("name",testee.getName());
	}

	@Test
	public void testCopyConstructorS3IncludesBucketName() {
		String testClientOptionsString = "S3_BUCKET_NAME=my-test-bucket";
		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientOptions(testClientOptionsString);
		fileSystem.setClientType(NfsClientType.S3);
		fileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
		NfsFileSystemInfo testee = new NfsFileSystemInfo(fileSystem);
		assertEquals("my-test-bucket", testee.getOptions().get("S3_BUCKET_NAME"));
		assertEquals("S3", testee.getClientType());
	}

	@Test
	public void testCopyConstructorUserDoesNotRequiresRootDirs() {

		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientType(NfsClientType.SFTP);
		fileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
		NfsFileSystemInfo testee = new NfsFileSystemInfo(fileSystem);
		assertEquals(null,testee.getOptions().get("USER_DIRS_REQUIRED"));
		String testClientOptionsString = "USER_DIRS_REQUIRED=false";
		fileSystem.setClientOptions(testClientOptionsString);
		testee = new NfsFileSystemInfo(fileSystem);
		assertEquals(null,testee.getOptions().get("USER_DIRS_REQUIRED"));
	}

}
