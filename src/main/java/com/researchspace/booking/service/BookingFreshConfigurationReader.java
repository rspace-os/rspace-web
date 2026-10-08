package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.booking.BookableTargetReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads whether a target is bookable using a fresh, read-committed database snapshot. */
@Component
@RequiredArgsConstructor
public class BookingFreshConfigurationReader {
  private final BookingConfigurationDao configurations;

  /**
   * Reads the current configuration without taking its lock. The caller must already hold the
   * instrument lock, which prevents configuration creation or removal until that transaction ends.
   */
  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.READ_COMMITTED,
      readOnly = true)
  public boolean hasConfiguration(BookableTargetReference target) {
    return configurations.findByTarget(target).isPresent();
  }
}
