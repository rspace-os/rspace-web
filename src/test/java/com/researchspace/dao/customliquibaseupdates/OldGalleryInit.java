package com.researchspace.dao.customliquibaseupdates;

import com.axiope.model.record.init.UserFolderSetupImpl;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.UserFolderSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Sets up partial pre-1.36 folder set up, with Gallery in the templates folder */
@Component(value = "oldFolderSetup")
public class OldGalleryInit implements UserFolderCreator {

  private @Autowired RecordFactory recordFactory;
  private @Autowired IContentInitialiserUtils contentUtils;

  @Override
  public UserFolderSetup initStandardFolderStructure(User subject, Folder rootForUser) {
    UserFolderSetupImpl rc = new UserFolderSetupImpl();
    rc.setUserRoot(rootForUser);
    Folder rootMedia = recordFactory.createRootMediaFolder(subject);
    contentUtils.addChild(rootForUser, rootMedia, subject);
    rc.setMediaRoot(rootMedia);
    contentUtils.delayForUniqueCreationTime();

    Folder templateFolder =
        recordFactory.createSystemCreatedFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, subject);
    templateFolder.addType(RecordType.TEMPLATE);
    contentUtils.addChild(rootMedia, templateFolder, subject);
    rc.setTemplateFolder(templateFolder);
    return rc;
  }

  @Override
  public Folder createSharedSnippetFolder(User subject, Folder snippetFolder) {
    return null;
  }
}
