package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryFileDaoHibernate extends GenericDaoHibernate<InventoryFile, Long>
    implements InventoryFileDao {

  public InventoryFileDaoHibernate(Class<InventoryFile> persistentClass) {
    super(persistentClass);
  }

  public InventoryFileDaoHibernate() {
    super(InventoryFile.class);
  }

  @Override
  public InventoryFile getWithInitializedFields(Long id) {
    InventoryFile inventoryFile = get(id);
    inventoryFile.getFileProperty().getFileName(); // load fileProperty that is lazy-initialized
    return inventoryFile;
  }

  @Override
  public List<InventoryFile> findByMediaFileId(Long mediaFileId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryFile f where f.mediaFile.id = :mediaFileId and f.deleted = false",
            InventoryFile.class)
        .setParameter("mediaFileId", mediaFileId)
        .list();
  }

  @Override
  public List<InventoryAttachmentField> findAttachmentFieldsByMediaFileId(Long mediaFileId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select af from InventoryAttachmentField af join af.files f"
                + " where f.mediaFile.id = :mediaFileId and f.deleted = false",
            InventoryAttachmentField.class)
        .setParameter("mediaFileId", mediaFileId)
        .list();
  }
}
