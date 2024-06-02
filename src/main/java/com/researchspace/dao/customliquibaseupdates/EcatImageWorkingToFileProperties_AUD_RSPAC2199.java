package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.files.service.InternalFileStore;
import liquibase.database.Database;

public class EcatImageWorkingToFileProperties_AUD_RSPAC2199 extends BlobMigrationBase {

  @Override
  public String getConfirmationMessage() {
    return "Ecat working images_AUD transferred";
  }

  protected void doExecute(Database database) {
    String query =
        "select ecatIm.id as ecatImId, emf.fileName, iblob.id as blobId, iblob.data "
            + " from EcatImage_AUD ecatIm inner join BaseRecord br on br.id = ecatIm.id "
            + " inner join ImageBlob iblob on iblob.id=ecatIm.imageFileRezisedEditor_id "
            + " inner join EcatMediaFile emf on emf.id=ecatIm.id "
            + " where br.owner_id=:userId and ecatIm.imageFileRezisedEditor_id is not null "
            + " and ecatIm.workingImageFP_id is null";
    String category = InternalFileStore.IMG_WORKING_CATEGORY;
    String updateSQL =
        "update EcatImage_AUD set workingImageFP_id = :fpid where id = :id and"
            + " imageFileRezisedEditor_id = :blobId";
    String progressLogMsg = "Transferring only-in-EcatImage_AUD working images";
    migrate(query, category, updateSQL, progressLogMsg);
  }
}
