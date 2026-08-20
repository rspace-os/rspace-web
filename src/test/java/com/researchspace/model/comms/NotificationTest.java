package com.researchspace.model.comms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.researchspace.model.comms.data.ArchiveExportNotificationData;

public class NotificationTest {
	
	@Test
	public void testArchiveExportNotificationAndItsData() {

		Notification archiveNot = new Notification();
		archiveNot.setNotificationType(NotificationType.ARCHIVE_EXPORT_COMPLETED);
		
		// no data by default
		assertNull(archiveNot.getNotificationData());
		assertNull(archiveNot.getNotificationDataObject());
		
		// set null object
		archiveNot.setNotificationDataObject(null);
		assertNull(null, archiveNot.getNotificationData());
		
		// try setting empty data object
		ArchiveExportNotificationData data = new ArchiveExportNotificationData();
		archiveNot.setNotificationDataObject(data);
		assertEquals("{\"nfsLinksIncluded\":false}", archiveNot.getNotificationData());
		assertEquals(data, archiveNot.getNotificationDataObject());
		
		// try data object with just archive link
		ArchiveExportNotificationData dataWithLink = new ArchiveExportNotificationData();
		dataWithLink.setDownloadLink("https://testArchiveLink");
		archiveNot.setNotificationDataObject(dataWithLink);
		assertEquals("{\"downloadLink\":\"https://testArchiveLink\",\"nfsLinksIncluded\":false}", archiveNot.getNotificationData());

		ArchiveExportNotificationData retrievedDataWithLink = (ArchiveExportNotificationData) archiveNot.getNotificationDataObject();
		assertNotNull(retrievedDataWithLink);
		assertEquals(dataWithLink.getDownloadLink(), retrievedDataWithLink.getDownloadLink());
	}
}
