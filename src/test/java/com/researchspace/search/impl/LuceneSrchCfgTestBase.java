package com.researchspace.search.impl;

import com.axiope.search.SearchConfig;
import com.axiope.search.WorkspaceSearchConfig;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

public class LuceneSrchCfgTestBase extends SpringTransactionalTest {

  @Autowired LuceneSearchTermListFactory termListFactory;

  SearchConfig mutableCfg = null;
  LuceneSrchCfg luceneCfg = null;
  User user = null;

  @Before
  public void baseSetUp() {
    mutableCfg = new WorkspaceSearchConfig(user);
    luceneCfg = new LuceneSrchCfg(mutableCfg, termListFactory);
    user = TestFactory.createAnyUser("user");
  }
}
