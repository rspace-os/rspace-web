package com.researchspace.dao.customliquibaseupdates;

import com.researchspace.files.service.InternalFileStore;
import liquibase.database.Database;

public class ThumbnailBlobToFileProperties_RSPAC2199 extends BlobMigrationBase {

  @Override
  public String getConfirmationMessage() {
    return "Thumbnail thumbnails transferred";
  }

  protected void doExecute(Database database) {
    String query =
        "select thumb.id as thumbId, emf.fileName, iblob.id as blobId, iblob.data "
            + " from Thumbnail thumb inner join BaseRecord br on br.id = thumb.sourceId "
            + " inner join ImageBlob iblob on iblob.id=thumb.imageBlob_id "
            + " inner join EcatMediaFile emf on emf.id=thumb.sourceId "
            + " where br.owner_id=:userId and thumb.imageBlob_id is not null "
            + " and thumb.sourceType=0 "
            + // only image type, not chem images
            " and thumb.thumbnailFP_id is null";
    String category = InternalFileStore.THUMBNAIL_THUMBNAIL_CATEGORY;
    String updateSQL =
        "update Thumbnail set thumbnailFP_id = :fpid where id = :id and sourceType = 0 and"
            + " imageBlob_id = :blobId";
    String progressLogMsg = "Transferring Thumbnail thumbnails";
    migrate(query, category, updateSQL, progressLogMsg);
  }
}
