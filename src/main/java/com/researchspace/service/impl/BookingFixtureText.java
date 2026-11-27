package com.researchspace.service.impl;

import java.util.Locale;

/**
 * Names and notes of the Docker development booking fixtures. They are seed data for developers,
 * not user interface text, so they are deliberately not in the message catalogs.
 */
final class BookingFixtureText {

  /**
   * Marks fixture instruments and containers so a restart finds them instead of duplicating them.
   */
  static final String DESCRIPTION = "Docker development booking fixture";

  static final String INSTRUMENT_CONFOCAL = "Confocal microscope";
  static final String INSTRUMENT_ELECTRON_MICROSCOPE = "Electron microscope";
  static final String INSTRUMENT_MASS_SPECTROMETER = "Mass spectrometer";
  static final String INSTRUMENT_FLOW_CYTOMETER = "Flow cytometer";
  static final String INSTRUMENT_NO_PARENT_CENTRIFUGE = "No-parent centrifuge";
  static final String INSTRUMENT_DELETED_LOCATION_SEQUENCER = "Deleted-location sequencer";
  static final String INSTRUMENT_RESTRICTED_LOCATION_PLATE_READER =
      "Restricted-location plate reader";
  static final String INSTRUMENT_BOOKING_CARD_SETUP = "Booking card: Set up booking";
  static final String INSTRUMENT_BOOKING_CARD_BOOK = "Booking card: Book";
  static final String INSTRUMENT_BOOKING_CARD_DISABLED = "Booking card: Open (disabled)";
  static final String INSTRUMENT_BOOKING_CARD_ARCHIVED = "Booking card: Open (archived)";
  static final String INSTRUMENT_BOOKING_ALERTS = "Booking alerts test bench";
  static final String INSTRUMENT_AUCKLAND_PCR_CYCLER = "Auckland PCR cycler";
  static final String INSTRUMENT_HONOLULU_DIFFRACTOMETER = "Honolulu X-ray diffractometer";
  static final String INSTRUMENT_KOLKATA_NMR_SPECTROMETER = "Kolkata NMR spectrometer";
  static final String INSTRUMENT_CLOSED_DAY_INCUBATOR = "Closed-day incubator";

  static final String CONTAINER_IMAGING_LAB = "Imaging lab";
  static final String CONTAINER_CRYO_EM_SUITE = "Cryo-EM suite";
  static final String CONTAINER_LONG_CELL_ANALYSIS_FACILITY =
      "Cell analysis facility with a deliberately long location name";
  static final String CONTAINER_DELETED_INSTRUMENT_ROOM = "Deleted instrument room";
  static final String CONTAINER_RESTRICTED_INSTRUMENT_ROOM = "Restricted instrument room";

  static final String PURPOSE_CELL_IMAGING = "Cell imaging";
  static final String PURPOSE_CALIBRATION_RUN = "Calibration run";
  static final String PURPOSE_ULTRASTRUCTURE_IMAGING = "Ultrastructure imaging";
  static final String PURPOSE_PROTEOMICS_RUN = "Proteomics run";
  static final String PURPOSE_CELL_SORTING = "Cell sorting";
  static final String PURPOSE_OVERNIGHT_ANALYSIS = "Overnight analysis";
  static final String PURPOSE_BUSY_CALENDAR = "Busy calendar fixture";
  static final String PURPOSE_AURORA_CALIBRATION = "Aurora calibration session";
  static final String PURPOSE_BEACON_CELL_IMAGING = "Beacon cell imaging study";
  static final String PURPOSE_COMET_PROTEOMICS = "Comet proteomics analysis";
  static final String PURPOSE_BOOKING_ALERTS_PAST = "Booking alerts past fixture";
  static final String PURPOSE_BOOKING_ALERTS_OVERLAP = "Booking alerts overlap fixture";
  static final String PURPOSE_OVERLAPPING_PROTEOMICS_RUN = "Overlapping proteomics run";
  static final String PURPOSE_LATE_SPIN = "Late spin until midnight";
  static final String PURPOSE_TWO_DAY_SEQUENCING_RUN = "Two-day sequencing run";
  static final String PURPOSE_PCR_AMPLIFICATION = "PCR amplification";
  static final String PURPOSE_CRYSTAL_SCREENING = "Crystal screening";
  static final String PURPOSE_QUARTER_HOUR_NMR = "Quarter-hour NMR acquisition";
  static final String PURPOSE_CLOSED_DAY_BOOKING = "Booking on a day that is now closed";
  static final String PURPOSE_CLOSED_DAY_MAINTENANCE = "Maintenance on a closed day";

  private BookingFixtureText() {}

  /** The name of the {@code index}th busy-calendar instrument, from 1. */
  static String busyCalendarInstrument(int index) {
    return String.format(Locale.ROOT, "Busy calendar instrument %03d", index);
  }
}
