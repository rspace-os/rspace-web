package com.researchspace.service.impl;

import com.researchspace.dao.EcatChemistryFileDao;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.service.EcatChemistryFileManager;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("ecatChemistryFileManager")
@Transactional
public class EcatChemistryFileManagerImpl extends GenericManagerImpl<EcatChemistryFile, Long>
    implements EcatChemistryFileManager {

  @Autowired private EcatChemistryFileDao chemistryFileDao;
  @Autowired private IPermissionUtils permissionUtils;

  public EcatChemistryFileManagerImpl(@Autowired EcatChemistryFileDao dao) {
    super(dao);
  }

  @Override
  public EcatChemistryFile get(Long id, User user) {
    EcatChemistryFile chemistryFile = chemistryFileDao.get(id);
    if (!assertFilePermissions(PermissionType.READ, chemistryFile, user)) {
      throw new AuthorizationException(
          "Unauthorised attempt by user ["
              + user.getUsername()
              + "] to access chemistry file with id ["
              + id
              + "]");
    } else {
      return chemistryFile;
    }
  }

  @Override
  public EcatChemistryFile save(EcatChemistryFile chemistryFile, User user) {
    if (!assertFilePermissions(PermissionType.WRITE, chemistryFile, user)) {
      throw new AuthorizationException(
          "Unauthorised attempt by user ["
              + user.getUsername()
              + "] to save chemistry file ["
              + chemistryFile.getFileName()
              + "]");
    } else {
      return chemistryFileDao.save(chemistryFile);
    }
  }

  @Override
  public void remove(Long id, User user) {
    EcatChemistryFile chemistryFile = chemistryFileDao.get(id);
    if (!assertFilePermissions(PermissionType.DELETE, chemistryFile, user)) {
      throw new AuthorizationException(
          "Unauthorised attempt by user ["
              + user.getUsername()
              + "] to delete chemistry file with id ["
              + id
              + "]");
    } else {
      chemistryFileDao.remove(id);
    }
  }

  private boolean assertFilePermissions(
      PermissionType permissionType, EcatChemistryFile chemistryFile, User user) {
    return permissionUtils.isPermitted(chemistryFile, permissionType, user);
  }
}
