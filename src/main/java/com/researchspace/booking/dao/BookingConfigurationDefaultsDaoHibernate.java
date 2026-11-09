package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Repository;

@Repository("bookingConfigurationDefaultsDao")
public class BookingConfigurationDefaultsDaoHibernate
    extends GenericDaoHibernate<BookingConfigurationDefaults, Long>
    implements BookingConfigurationDefaultsDao {

  public BookingConfigurationDefaultsDaoHibernate(SessionFactory sessionFactory) {
    super(BookingConfigurationDefaults.class, sessionFactory);
  }

  @Override
  public Optional<BookingConfigurationDefaults> lockSingleton() {
    return getSession()
        .createQuery(
            "from BookingConfigurationDefaults where id = :id", BookingConfigurationDefaults.class)
        .setParameter("id", BookingConfigurationDefaults.SINGLETON_ID)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .uniqueResultOptional();
  }

  @Override
  public BookingConfigurationDefaults saveAndFlush(BookingConfigurationDefaults defaults) {
    BookingConfigurationDefaults saved = save(defaults);
    getSession().flush();
    return saved;
  }
}
