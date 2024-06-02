package com.researchspace.service.impl;

import com.researchspace.model.User;
import com.researchspace.model.record.Notebook;
import com.researchspace.service.IApplicationInitialisor;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class DBDataIntegrityCheckerTest extends SpringTransactionalTest {

  @Autowired
  @Qualifier("dBDataIntegrityChecker")
  IApplicationInitialisor integrityChecker;

  @Test
  public void testOnAppStartup() {
    integrityChecker.onAppStartup(applicationContext);
  }

  @Test
  public void testTooManyNotebookParents() throws InterruptedException {
    User u = createAndSaveRandomUser();
    initialiseContentWithExampleContent(u);
    Notebook n1 = createNotebookWithNEntries(u.getRootFolder().getId(), "any", 2, u);
    Notebook n2 = createNotebookWithNEntries(u.getRootFolder().getId(), "any", 2, u);
    folderMgr.addChild(n2.getId(), n1.getChildrens().iterator().next(), u);
    integrityChecker.onAppStartup(applicationContext);
  }
}
