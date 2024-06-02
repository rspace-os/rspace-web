package com.researchspace.webapp.integrations.wopi;

import static org.junit.Assert.assertEquals;

import com.researchspace.service.MediaFileLockHandler;
import java.io.IOException;
import org.junit.Test;

public class WopiLocksHandlerTest {

  private MediaFileLockHandler lockHandler = new MediaFileLockHandler();

  @Test
  public void testLockRelockUnlockSequence() throws IOException {

    String fileId = "GL12";
    String lockId = "lock1";
    String lockId2 = "lock2";

    // assert no lock
    assertEquals("", lockHandler.getLock(fileId));

    // requesting lock for user/resource returns this lock id
    String returnedLockMismatchedValue = lockHandler.lock(fileId, lockId);
    assertEquals(null, returnedLockMismatchedValue);
    assertEquals(lockId, lockHandler.getLock(fileId));

    // requesting lock with different id for the same user/resource returns old lock id
    returnedLockMismatchedValue = lockHandler.lock(fileId, lockId2);
    assertEquals(lockId, returnedLockMismatchedValue);
    assertEquals(lockId, lockHandler.getLock(fileId));

    // requesting re-lock with different id for the same user/resource returns new lock id
    returnedLockMismatchedValue = lockHandler.unlockAndRelock(fileId, lockId, lockId2);
    assertEquals(null, returnedLockMismatchedValue);
    assertEquals(lockId2, lockHandler.getLock(fileId));

    // unlocking
    returnedLockMismatchedValue = lockHandler.unlock(fileId, lockId2);
    assertEquals(null, returnedLockMismatchedValue);
    assertEquals("", lockHandler.getLock(fileId));
  }

  @Test
  public void testLockRefreshingAndExpiring() throws InterruptedException {

    // 1000ms lock expiration time
    lockHandler.setLockDurationInMilis(1000);

    String fileId = "GL12";
    String fileId2 = "GL122";
    String lockId = "lock1";
    String lockId2 = "lock12";

    // lock both resources
    lockHandler.lock(fileId, lockId);
    lockHandler.lock(fileId2, lockId2);
    assertEquals(lockId, lockHandler.getLock(fileId));
    assertEquals(lockId2, lockHandler.getLock(fileId2));

    // wait 500ms, refresh lock on second resource
    Thread.sleep(500);
    lockHandler.refreshLock(fileId2, lockId2);
    assertEquals(lockId, lockHandler.getLock(fileId));
    assertEquals(lockId2, lockHandler.getLock(fileId2));

    // wait 500ms, lock on first resource should expire by now
    Thread.sleep(500);
    assertEquals("", lockHandler.getLock(fileId));
    assertEquals(lockId2, lockHandler.getLock(fileId2));

    // wait 500ms, lock on both resources should be expired by now
    Thread.sleep(500);
    assertEquals("", lockHandler.getLock(fileId));
    assertEquals("", lockHandler.getLock(fileId2));
  }
}
