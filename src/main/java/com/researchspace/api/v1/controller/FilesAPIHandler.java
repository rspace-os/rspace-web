package com.researchspace.api.v1.controller;

import com.researchspace.CacheNames;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.model.record.Record;
import com.researchspace.service.RecordManager;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;

public class FilesAPIHandler {

  protected @Autowired RecordManager recordManager;
  protected @Autowired IPermissionUtils permissionUtils;
  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  /**
   * GEts an {@link ApiFile} or <code>null</code> if user not permitted or underlying {@link
   * EcatMediaFile} does not exist
   *
   * @param id the id of the {@link EcatMediaFile};
   * @param user
   * @return
   */
  @Cacheable(cacheNames = CacheNames.APIFILE, key = "#id")
  public ApiFile getFile(Long id, User user) {
    EcatMediaFile mediaFile = getMediaFileIfPermitted(id, user, true);
    return (mediaFile != null) ? new ApiFile(mediaFile) : null;
  }

  public EcatMediaFile getMediaFileIfPermitted(Long id, User user, boolean logUnauthorizedRequest) {
    EcatMediaFile mediaFile = null;
    Optional<Record> optionalFile = recordManager.getSafeNull(id);
    if (optionalFile.isPresent()) {
      Record record = optionalFile.get();
      if (record instanceof EcatMediaFile) {
        boolean canRead =
            permissionUtils.isRecordAccessPermitted(user, record, PermissionType.READ);
        if (canRead) {
          mediaFile = (EcatMediaFile) record;
        } else if (logUnauthorizedRequest) {
          SECURITY_LOG.warn(
              "Unauthorised API call by user [{}] to access media file [{}]",
              user.getUsername(),
              record.getGlobalIdentifier());
        }
      }
    }

    return mediaFile;
  }
}
