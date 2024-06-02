package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.files.service.InternalFileStore;
import liquibase.database.Database;

public class EcatImagesToFileProperties_RSPAC2199 extends BlobMigrationBase {

  @Override
  public String getConfirmationMessage() {
    return "Ecat image thumbnails transferred";
  }

  protected void doExecute(Database database) {
    String query =
        "select ecatIm.id as ecatImId, emf.fileName, iblob.id as blobId, iblob.data "
            + " from EcatImage ecatIm inner join BaseRecord br on br.id = ecatIm.id "
            + " inner join ImageBlob iblob on iblob.id=ecatIm.imageThumbnailed_id "
            + " inner join EcatMediaFile emf on emf.id=ecatIm.id "
            + " where br.owner_id=:userId and ecatIm.imageThumbnailed_id is not null "
            + " and ecatIm.thumbnailImageFP_id is null";
    String category = InternalFileStore.IMG_THUMBNAIL_CATEGORY;
    String updateSQL =
        "update EcatImage set thumbnailImageFP_id = :fpid where id = :id and imageThumbnailed_id ="
            + " :blobId";
    String progressLogMsg = "Transferring EcatImage thumbnails";
    migrate(query, category, updateSQL, progressLogMsg);
  }
}
