package com.researchspace.model.core;

/** 2-letter id codes to prefix database IDs, to generate global IDs across RSpace. */
public enum GlobalIdPrefix {

  /** Folder resource */
  FL,

  /** Notebook */
  NB,

  /** Record ( a structured document or notebook entry) */
  SD,

  /** Message */
  MG,

  /** Gallery record (media file) */
  GL,

  /** Gallery Folder */
  GF,

  /** Form */
  FM,

  /** Field */
  FD,

  /** Snippet */
  ST,

  /** User */
  US,

  /** Group */
  GP,

  /** Chemistry structure */
  CH,

  /** ImageAnnotation */
  IA,

  /** Field form */
  FF,

  /** Comment item */
  CM,

  /** Comment (collection of comment items) */
  CT,

  /** Thumbnail */
  TH,

  /** Math */
  MA,

  /** NetFileStore */
  NF,

  /** Inventory Sample */
  SA,

  /** Inventory SubSample */
  SS,

  /** Inventory Field */
  SF,

  /** Extra (ad-hoc) Field */
  EF,

  /** (Inventory) SampleTemplate */
  IT,

  /** (Inventory) Container */
  IC,

  /** (Inventory) Workbench */
  BE,

  /** (Inventory) File */
  IF,

  /** (Inventory) Instrument */
  IN,

  /** (Inventory) InstrumentTemplate */
  NT,

  /** List of Materials */
  LM,

  /** (Inventory) Basket */
  BA,

  /*
   * ================
   *   for testing
   * ================
   */

  /** Linked folder - testing only */
  LF
}
