package com.researchspace.model.inventory;

/**
 * Simple discriminator for whether sample is lab-created, obtained from Vendor or other (e.g. a
 * gift)
 */
public enum SampleSource {

  /** Sample was created in the lab */
  LAB_CREATED,

  /** Sample is acquired from a vendor */
  VENDOR_SUPPLIED,

  /** Catch-all category e.g. for gift, requested sample etc */
  OTHER
}
