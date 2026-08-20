package com.researchspace.model.netfiles;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.Test;

import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;

public class NfsFileSystemTest {
	
	@Test
	public void parsingClientOptions() {
		
		String testClientOptionsString = "SAMBA_DOMAIN=testDomain\nSAMBA_SHARE_NAME=testShare";
		
		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientOptions(testClientOptionsString);
		assertEquals("testDomain", fileSystem.getClientOption(NfsFileSystemOption.SAMBA_DOMAIN));
		assertEquals("testShare", fileSystem.getClientOption(NfsFileSystemOption.SAMBA_SHARE_NAME));

		fileSystem.setClientOption(NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY, "test2");
		assertEquals("test2", fileSystem.getClientOption(NfsFileSystemOption.SFTP_SERVER_PUBLIC_KEY));
		
		assertEquals("SAMBA_DOMAIN=testDomain\nSAMBA_SHARE_NAME=testShare\nSFTP_SERVER_PUBLIC_KEY=test2\n", 
		        fileSystem.getClientOptions());
	}

	@Test
	public void testFileSystemRequiresUserRootDirs() {

		String testClientOptionsString = "USER_DIRS_REQUIRED=true";
		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientOptions(testClientOptionsString);
		assertEquals(true, fileSystem.fileSystemRequiresUserRootDirs());
		fileSystem = new NfsFileSystem();
		assertEquals(false, fileSystem.fileSystemRequiresUserRootDirs());
		testClientOptionsString = "USER_DIRS_REQUIRED=false";
		fileSystem.setClientOptions(testClientOptionsString);
		assertEquals(false, fileSystem.fileSystemRequiresUserRootDirs());
	}

	@Test
	public void conversionToFileSystemInfo() {
		
		String testClientDetailsString = "SAMBA_DOMAIN=testDomain\nSAMBA_SHARE_NAME=testShare";
		
		NfsFileSystem sambaFileSystem = new NfsFileSystem();
		sambaFileSystem.setClientType(NfsClientType.SAMBA);
		sambaFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
		sambaFileSystem.setClientOptions(testClientDetailsString);

		NfsFileSystemInfo sambaInfo = sambaFileSystem.toFileSystemInfo();
		assertEquals("SAMBA", sambaInfo.getClientType());
		assertTrue(sambaInfo.getOptions().isEmpty());

		sambaFileSystem.setClientType(NfsClientType.SMBJ);
		NfsFileSystemInfo smbjInfo = sambaFileSystem.toFileSystemInfo();
		assertEquals("SMBJ", smbjInfo.getClientType());
		assertEquals(1, smbjInfo.getOptions().size());
		assertEquals("testShare", smbjInfo.getOptions().get(NfsFileSystemOption.SAMBA_SHARE_NAME.toString()));
	}

	
}
