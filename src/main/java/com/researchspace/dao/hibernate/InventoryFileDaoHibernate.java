package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryFileDao;
import com.researchspace.model.inventory.InventoryFile;
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
}
