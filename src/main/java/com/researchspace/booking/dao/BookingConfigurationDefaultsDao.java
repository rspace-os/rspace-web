package com.researchspace.booking.dao;

import com.researchspace.dao.GenericDao;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import java.util.Optional;

public interface BookingConfigurationDefaultsDao
    extends GenericDao<BookingConfigurationDefaults, Long> {

  Optional<BookingConfigurationDefaults> lockSingleton();

  BookingConfigurationDefaults saveAndFlush(BookingConfigurationDefaults defaults);
}
