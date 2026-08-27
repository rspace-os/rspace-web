package com.researchspace.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.UserFolderSetup;
import org.mockito.Mockito;

/**
 * Creates a user account folder structure, with permissions using {@link DefaultUserFolderCreator}
 * with all DAO/service calls mocked out, so it is not persisted to the DB <br>
 * I.e this is for testing folder relations/permissions in unit tests without needing to run Spring
 * tests. <br>
 */
public class MockFolderStructure {

  FolderDao folderDao = Mockito.mock(FolderDao.class);
  UserDao userDao = Mockito.mock(UserDao.class);
  RecordDao recordDao = Mockito.mock(RecordDao.class);
  private FolderManager folderManagerMock = Mockito.mock(FolderManager.class);

  public UserFolderSetup create(User subject) {
    return create(subject, null);
  }

  public UserFolderSetup create(User subject, FolderManager extfolderManagerMock) {
    if (extfolderManagerMock != null) {
      folderManagerMock = extfolderManagerMock;
    }
    // use concrete classes to set up permissions and folders correctly
    RecordFactory rfac = new RecordFactory();
    PermissionFactory permFac = new DefaultPermissionFactory();
    // mock out the DAOs
    IContentInitialiserUtils utils =
        new ContentInitialiserUtilsImpl(permFac, rfac, userDao, recordDao, folderDao);
    // mimic method chaining when saving an object to avoid null values being returned from mock
    // saves
    setupMockDaos();
    Folder root = utils.setupRootFolder(subject);
    DefaultUserFolderCreator folderCreator =
        new DefaultUserFolderCreator(rfac, permFac, folderDao, utils, folderManagerMock);

    return folderCreator.initStandardFolderStructure(subject, root);
  }

  private void setupMockDaos() {
    when(folderDao.save(any(Folder.class)))
        .thenAnswer(
            invocation -> {
              return invocation.getArgument(0);
            });
    when(userDao.save(any(User.class)))
        .thenAnswer(
            invocation -> {
              return invocation.getArgument(0);
            });
  }
}
