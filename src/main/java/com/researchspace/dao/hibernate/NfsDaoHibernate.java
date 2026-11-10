package com.researchspace.dao.hibernate;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.StructuredDocument;
import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** For manipulating net file store related entities. */
@Repository("nfsDao")
public class NfsDaoHibernate implements NfsDao {

  private final transient Logger log = LoggerFactory.getLogger(NfsDaoHibernate.class);

  private SessionFactory sessionFactory;

  @Autowired
  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public NfsFileStore getNfsFileStore(Long id) {
    NfsFileStore folder =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileStore where id=:fileStoreId", NfsFileStore.class)
            .setParameter("fileStoreId", id)
            .uniqueResult();
    return folder;
  }

  @Override
  public void saveNfsFileStore(NfsFileStore fileStore) {
    Session ss = sessionFactory.getCurrentSession();
    ss.merge(fileStore);
    ss.flush();
  }

  @Override
  public void deleteNfsFileStore(NfsFileStore fileStore) {
    Session ss = sessionFactory.getCurrentSession();
    Object mergedFileStore = ss.merge(fileStore);
    ss.delete(mergedFileStore);
    ss.flush();
  }

  @Override
  public List<NfsFileStore> getFileStores() {
    List<NfsFileStore> fileStores =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileStore", NfsFileStore.class)
            .list();
    return fileStores;
  }

  @Override
  public List<NfsFileStore> getUserFileStores(Long userId) {
    List<NfsFileStore> fileStores =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "from NfsFileStore where user.id=:userId and deleted=false", NfsFileStore.class)
            .setParameter("userId", userId)
            .list();
    return fileStores;
  }

  @Override
  public List<NfsFileSystem> getFileSystems() {
    List<NfsFileSystem> fileSystems =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileSystem order by id", NfsFileSystem.class)
            .list();
    return fileSystems;
  }

  @Override
  public List<NfsFileSystem> getActiveFileSystems() {
    List<NfsFileSystem> fileSystems =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileSystem where disabled=false order by id", NfsFileSystem.class)
            .list();
    return fileSystems;
  }

  @Override
  public NfsFileSystem getNfsFileSystem(Long id) {
    return sessionFactory.getCurrentSession().get(NfsFileSystem.class, id);
  }

  @Override
  public void saveNfsFileSystem(NfsFileSystem fileSystem) {
    Session ss = sessionFactory.getCurrentSession();
    ss.merge(fileSystem);
    ss.flush();
  }

  @Override
  public boolean deleteNfsFileSystem(Long id) {

    Session ss = sessionFactory.getCurrentSession();
    int fileStoresCount =
        ((Number)
                ss.createNativeQuery("select count(*) from NfsFileStore where fileSystem_id=:id")
                    .setParameter("id", id)
                    .getSingleResult())
            .intValue();

    log.info("found " + fileStoresCount + " file stores when deleting file system " + id);
    if (fileStoresCount > 0) {
      log.info("skipping file system deletion as there are connected file stores");
      return false;
    }

    Object mergedFileSystem = ss.merge(getNfsFileSystem(id));
    ss.delete(mergedFileSystem);
    ss.flush();
    return true;
  }

  @Override
  public List<NfsFieldLink> getFieldLinks(Long fieldId) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from NfsFieldLink where field.id = :fieldId", NfsFieldLink.class)
        .setParameter("fieldId", fieldId)
        .list();
  }

  @Override
  public void saveFieldLink(NfsFieldLink link) {
    sessionFactory.getCurrentSession().persist(link);
  }

  @Override
  public void deleteFieldLink(NfsFieldLink link) {
    sessionFactory.getCurrentSession().remove(link);
  }

  @Override
  public List<StructuredDocument> getDocumentsLinkingTo(
      Long fileSystemId, Collection<String> absolutePaths) {
    if (absolutePaths.isEmpty()) {
      return List.of();
    }
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "select distinct doc from NfsFieldLink l join l.field f join f.structuredDocument doc"
                + " where l.fileSystem.id = :fsId and l.absolutePath in (:paths)"
                + " and doc.deleted = false order by doc.id",
            StructuredDocument.class)
        .setParameter("fsId", fileSystemId)
        .setParameterList("paths", absolutePaths)
        .list();
  }
}
