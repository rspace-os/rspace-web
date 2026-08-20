package com.researchspace.model.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.model.User;

class RecordToFolderTest {
	
	RecordToFolder r2f; 
	
	@BeforeEach
	public void before () {
		User user = TestFactory.createAnyUser("any");
		Folder f = TestFactory.createAFolder("f1", user);
		Folder f2 = TestFactory.createAFolder("f2", user);	
		f.addChild(f2, user, true);
		r2f = f2.getParents().iterator().next();
	}

	@Test
	public void deletedDate() {
		// initially null - not deleted
		assertNull(r2f.getDeletedDate());
		
		// check date is now. more or less
		r2f.markRecordInFolderDeleted(true);
		Date stored = r2f.getDeletedDate();
		assertNotNull(stored);
		// assert deletion date is now
		assertEquals(DateUtils.ceiling(stored, Calendar.MINUTE),
				DateUtils.ceiling(new Date(), Calendar.MINUTE) );
		
		// after undeleting, deletion date is now null again.
		r2f.markRecordInFolderDeleted(false);
		assertNull(r2f.getDeletedDate());
	}
}
