package com.researchspace.model.booking;

/**
 * Opening hours for one open ISO weekday that differ from the shared daily interval.
 *
 * @param dayOfWeek ISO weekday, 1 (Monday) to 7 (Sunday)
 * @param start inclusive opening time as {@code HH:mm}
 * @param end closing time as {@code HH:mm}, where {@code 24:00} means the next local midnight
 */
public record BookingOpeningException(int dayOfWeek, String start, String end) {}
