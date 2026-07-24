package com.axiope.search;

/** Field name constants for Lucene Search Config (term names) */
public class FieldNames {

  public static final String FIELD_DATA = "fields.fieldData";
  // Flat Hibernate Search field combining description and globalIdentifier
  // (BaseRecord.getSearchableContent()) and EcatCommentItem content. Full-text search must
  // cover both this and FIELD_DATA.
  public static final String FLAT_FIELD_DATA = "fields_fieldData";

  public static final String DOC_TAG = "docTag";

  public static final String NAME = "name";

  public static final String FORM_NAME = "formName";

  public static final String MODIFICATION_DATE = "modifiedDate";

  public static final String CREATION_DATE = "creationDate";

  public static final String OWNER = "owner.username";

  public static final String TEMPLATE = "template";

  public static final String FORM_STABLE_ID = "formStableId";

  /*
   * term names used in inventory
   */
  public static final String INV_TAGS = "tags";

  public static final String DESCRIPTION = "description";

  public static final String BARCODE = "barcodes.barcodeData";

  public static final String BARCODE_FIELD_DATA = "barcodes.fieldData";

  // Extra field content on Sample and SubSample (indexed via @IndexedEmbedded with "extraFields."
  // prefix)
  public static final String EXTRA_FIELD_DATA = "extraFields.fieldData";

  // SubSample note content (indexed via @IndexedEmbedded with "notes." prefix on SubSample)
  public static final String NOTES_FIELD_DATA = "notes.fieldData";

  // Attachment filenames (names only, not file contents) on Sample, SubSample and Container
  // (indexed via @IndexedEmbedded with "files." prefix; InventoryFile.fileName ->
  // files.fieldData)
  public static final String FILES_FIELD_DATA = "files.fieldData";

  /* for searching items by the id of the container they belong to */
  public static final String PARENT_ID = "parentId";

  /* for searching samples by id of the template they were created from */
  public static final String PARENT_TEMPLATE_ID = "parentTemplateId";

  /* for searching subsamples by id of the sample they belong to */
  public static final String PARENT_SAMPLE_ID = "parentSampleId";
}
