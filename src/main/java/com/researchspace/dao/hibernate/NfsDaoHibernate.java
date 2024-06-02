package com.researchspace.dao.hibernate;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import java.math.BigInteger;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;

/** For manipulating net file store related entities. */
@Repository("nfsDao")
public class NfsDaoHibernate implements NfsDao {

  private final transient Logger log = LoggerFactory.getLogger(NfsDaoHibernate.class);

  private SessionFactory sessionFactory;

  @Autowired
  @Required
  public void setSessionFactory(SessionFactory sessionFactory) {
    this.sessionFactory = sessionFactory;
  }

  @Override
  public NfsFileStore getNfsFileStore(Long id) {
    NfsFileStore folder =
        (NfsFileStore)
            sessionFactory
                .getCurrentSession()
                .createQuery("from NfsFileStore where id=:fileStoreId")
                .setLong("fileStoreId", id)
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
    @SuppressWarnings("unchecked")
    List<NfsFileStore> fileStores =
        sessionFactory.getCurrentSession().createQuery("from NfsFileStore").list();
    return fileStores;
  }

  @Override
  public List<NfsFileStore> getUserFileStores(Long userId) {
    @SuppressWarnings("unchecked")
    List<NfsFileStore> fileStores =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileStore where user_id=:userId and deleted=false")
            .setLong("userId", userId)
            .list();
    return fileStores;
  }

  @Override
  public List<NfsFileSystem> getFileSystems() {
    @SuppressWarnings("unchecked")
    List<NfsFileSystem> fileSystems =
        sessionFactory.getCurrentSession().createQuery("from NfsFileSystem order by id").list();
    return fileSystems;
  }

  @Override
  public List<NfsFileSystem> getActiveFileSystems() {
    @SuppressWarnings("unchecked")
    List<NfsFileSystem> fileSystems =
        sessionFactory
            .getCurrentSession()
            .createQuery("from NfsFileSystem where disabled=false order by id")
            .list();
    return fileSystems;
  }

  @Override
  public NfsFileSystem getNfsFileSystem(Long id) {
    return (NfsFileSystem) sessionFactory.getCurrentSession().get(NfsFileSystem.class, id);
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
        ((BigInteger)
                ss.createSQLQuery("select count(*) from NfsFileStore where fileSystem_id=:id")
                    .setParameter("id", id)
                    .uniqueResult())
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
}
