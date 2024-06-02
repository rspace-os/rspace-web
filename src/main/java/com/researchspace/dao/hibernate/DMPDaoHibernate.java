package com.researchspace.dao.hibernate;

import com.researchspace.dao.DMPDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class DMPDaoHibernate extends GenericDaoHibernate<DMPUser, Long> implements DMPDao {
  public DMPDaoHibernate() {
    super(DMPUser.class);
  }

  @Override
  public List<DMPUser> findDMPsForUser(User subject) {
    return getSession()
        .createQuery("from DMPUser du where du.user = :user", DMPUser.class)
        .setParameter("user", subject)
        .list();
  }

  @Override
  public List<DMPUser> findDMPsByPDF(User subject, Long ecatDocument_id) {
    return getSession()
        .createQuery(
            "from DMPUser du where du.user = :user and du.dmpDownloadPdf.id=:pdfIid ",
            DMPUser.class)
        .setParameter("user", subject)
        .setParameter("pdfIid", ecatDocument_id)
        .list();
  }

  @Override
  public Optional<DMPUser> findByDmpId(String dmpId, User subject) {
    return getSession()
        .createQuery("from DMPUser du where du.user = :user and du.dmpId =:dmpId", DMPUser.class)
        .setParameter("user", subject)
        .setParameter("dmpId", dmpId)
        .uniqueResultOptional();
  }
}
