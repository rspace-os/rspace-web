package com.researchspace.testutils;

import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;

public class NetFilesTestFactory {
	
	/**
	 * Creates a test NfsFileSystem setting in all properties (except Db id)
	 * @return
	 */
	public static  NfsFileSystem createAnyNfsFileSystem() {
		NfsFileSystem testFileSystem = new NfsFileSystem();
		testFileSystem.setClientType(NfsClientType.SAMBA);
		testFileSystem.setClientOptions("testSambaDetails");
		testFileSystem.setAuthType(NfsAuthenticationType.PASSWORD);
		testFileSystem.setAuthOptions("testAuthDetails");
		testFileSystem.setUrl("pangolin.researchspace.com:22");
		return testFileSystem;
	}
	
	/**
	 * Creates a test NfsFileStore, using <code>createAnyNfsFileSystem()</code> to create a filesystem. <br/>
	 * Doesn't set ID
	 * @param user
	 * @return the newly created {@link NfsFileStore}
	 */
	public static NfsFileStore createAnyNfsFileStore(User user) {
		NfsFileStore fileStore = new NfsFileStore();
		fileStore.setPath("cse/inf/rspacestore");
		fileStore.setUser(user);
		fileStore.setFileSystem(createAnyNfsFileSystem());
		return fileStore;
	}

}
