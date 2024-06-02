package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.ChildAddPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.IContentInitialiserUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Utility methods for content creation during accoutn setup; assumes is running inside existing
 * transaction.
 */
public class ContentInitialiserUtilsImpl implements IContentInitialiserUtils {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected @Autowired IRecordFactory recordFactory;
  protected @Autowired FolderDao folderDao;
  protected @Autowired UserDao userDao;
  protected @Autowired PermissionFactory permFactory;
  protected @Autowired RecordDao recordDao;

  // for spring
  public ContentInitialiserUtilsImpl() {}

  // for testing
  public ContentInitialiserUtilsImpl(
      PermissionFactory permissionFactory,
      RecordFactory recordFactory,
      UserDao userDao,
      RecordDao recordDao,
      FolderDao folderDao) {
    this.permFactory = permissionFactory;
    this.recordFactory = recordFactory;
    this.folderDao = folderDao;
    this.userDao = userDao;
    this.recordDao = recordDao;
  }

  @Override
  public Folder setupRootFolder(User user) {

    Folder rootForUser;
    log.info("No root folder found for this user, creating one");
    rootForUser = recordFactory.createRootFolder(user.getUsername(), user);

    setUpACLForUserFolder(user, rootForUser);
    rootForUser = folderDao.save(rootForUser);
    user.setRootFolder(rootForUser);

    userDao.save(user);
    return rootForUser;
  }

  // don't want subclasses messing with this
  private void setUpACLForUserFolder(User u, Folder rootForUser) {
    permFactory.setUpACLForUserRoot(u, rootForUser);
  }

  @Override
  public Folder addChild(Folder f, BaseRecord newTransientChild, User owner) {
    return addChild(f, newTransientChild, owner, ACLPropagationPolicy.DEFAULT_POLICY);
  }

  @Override
  public Folder addChild(
      Folder f, BaseRecord newTransientChild, User owner, ACLPropagationPolicy aclpolicy)
      throws IllegalAddChildOperation {
    saveChild(newTransientChild);
    f.addChild(newTransientChild, ChildAddPolicy.DEFAULT, owner, aclpolicy);
    folderDao.save(f);
    saveChild(newTransientChild);
    return f;
  }

  private void saveChild(BaseRecord newTransientChild) {
    if (newTransientChild.isFolder()) {
      folderDao.save((Folder) newTransientChild);
    } else {
      recordDao.save((Record) newTransientChild);
    }
  }

  @Override
  public void delayForUniqueCreationTime() {
    try {
      Thread.sleep(2);
    } catch (InterruptedException e) {
      log.warn("Interrupted while waiting for unique creation time.", e);
    }
  }
}
