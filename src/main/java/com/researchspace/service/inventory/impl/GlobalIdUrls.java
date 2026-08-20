package com.researchspace.service.inventory.impl;

/**
 * The shape of an inventory record's globalId address.
 *
 * <p>All that is left of a class that used to build these addresses, for the auto-fill that wrote
 * one into every instrument's Landing page field. That fill is retired (ADR 0006 item 3), so RSpace
 * no longer composes such an address; it only has to recognise the ones the fill already wrote, so
 * that they read as an empty field rather than as something a user chose.
 *
 * <p>The path segment stays shared rather than inlined at the one place that matches on it: it is
 * the only durable trace of the retired fill, and a second copy appearing elsewhere would be able
 * to disagree with it about which addresses count as auto-filled.
 */
final class GlobalIdUrls {

  /** The path segment that marks an address as a record's globalId page. */
  static final String GLOBAL_ID_PATH = "/globalId/";

  private GlobalIdUrls() {}
}
