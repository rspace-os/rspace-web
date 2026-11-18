package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import java.util.Collection;
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
    // both the attachment and its owning field must be live: deleting a field only flips the
    // field's flag, leaving its InventoryFile non-deleted, so filtering the file alone would
    // surface a stale back-reference. Mirrors the field-soft-delete filter the link queries use.
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select af from InventoryAttachmentField af join af.files f"
                + " where f.mediaFile.id = :mediaFileId and f.deleted = false"
                + " and af.deleted = false",
            InventoryAttachmentField.class)
        .setParameter("mediaFileId", mediaFileId)
        .list();
  }

  @Override
  public List<InventoryFile> findByMediaFileIds(Collection<Long> mediaFileIds) {
    if (mediaFileIds == null || mediaFileIds.isEmpty()) {
      return List.of();
    }
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InventoryFile f where f.mediaFile.id in (:mediaFileIds) and f.deleted = false",
            InventoryFile.class)
        .setParameterList("mediaFileIds", mediaFileIds)
        .list();
  }

  @Override
  public List<InventoryAttachmentField> findAttachmentFieldsByMediaFileIds(
      Collection<Long> mediaFileIds) {
    if (mediaFileIds == null || mediaFileIds.isEmpty()) {
      return List.of();
    }
    // same live-file and live-field rule as findAttachmentFieldsByMediaFileId
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select distinct af from InventoryAttachmentField af join af.files f"
                + " where f.mediaFile.id in (:mediaFileIds) and f.deleted = false"
                + " and af.deleted = false",
            InventoryAttachmentField.class)
        .setParameterList("mediaFileIds", mediaFileIds)
        .list();
  }
}
