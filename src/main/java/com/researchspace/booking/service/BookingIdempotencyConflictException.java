package com.researchspace.booking.service;

/** The caller reused a create identity for a different normalized request. */
public class BookingIdempotencyConflictException extends RuntimeException {}
