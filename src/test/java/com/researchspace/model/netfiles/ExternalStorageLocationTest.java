package com.researchspace.model.netfiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;

public class ExternalStorageLocationTest {

	private User operationUser;
	private EcatMediaFile connectedMediaFile;
	private NfsFileStore fileStore;
	private ExternalStorageLocation toTest;

	@Before
	public void setUp() throws Exception {
		String testClientOptionsString =
				"IRODS_ZONE=tempZone\nIRODS_HOME_DIR=/tempZone/home/alice\nIRODS_PORT=1247\n";
		NfsFileSystem fileSystem = new NfsFileSystem();
		fileSystem.setClientOptions(testClientOptionsString);

		operationUser = TestFactory.createAnyUser("user1");
		fileStore = new NfsFileStore();
		fileStore.setUser(operationUser);
		fileStore.setFileSystem(fileSystem);

		connectedMediaFile = TestFactory.createEcatImage(10L);

		toTest = new ExternalStorageLocation();
		toTest.setExternalStorageId(1L);
		toTest.setFileStore(fileStore);
		toTest.setConnectedMediaFile(connectedMediaFile);
		toTest.setOperationUser(operationUser);
	}

	@Test
	public void testObjectIsFilled() {
		assertEquals(1L, toTest.getExternalStorageId().longValue());
		assertEquals(fileStore, toTest.getFileStore());
		assertEquals(connectedMediaFile, toTest.getConnectedMediaFile());
		assertEquals(operationUser, toTest.getOperationUser());
		assertNull(toTest.getId());
	}

}
