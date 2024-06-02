package com.axiope.search;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** String constants that should be present in options[] when performing a search. */
public class SearchConstants {

  public static final String ALL_SEARCH_OPTION = "global";

  public static final String FULL_TEXT_SEARCH_OPTION = "fullText";

  public static final String TAG_SEARCH_OPTION = "tag";

  public static final String NAME_SEARCH_OPTION = "name";

  public static final String CREATION_DATE_SEARCH_OPTION = "created";

  public static final String MODIFICATION_DATE_SEARCH_OPTION = "lastModified";

  public static final String FORM_SEARCH_OPTION = "form";

  public static final String ATTACHMENT_SEARCH_OPTION = "attachment";

  public static final String FROM_TEMPLATE_SEARCH_OPTION = "template";

  public static final String OWNER_SEARCH_OPTION = "owner";

  public static final String RECORDS_SEARCH_OPTION = "records";

  public static final String INVENTORY_SEARCH_OPTION = "inventory";

  public static final String INVENTORY_PARENT_ID_OPTION = "inventoryParentId";

  public static final String INVENTORY_PARENT_TEMPLATE_ID_OPTION = "inventoryParentTemplateId";

  public static final String INVENTORY_PARENT_SAMPLE_ID_OPTION = "inventoryParentSampleId";

  private static final String[] SEARCH_OPTIONS =
      new String[] {
        ALL_SEARCH_OPTION,
        FULL_TEXT_SEARCH_OPTION,
        TAG_SEARCH_OPTION,
        NAME_SEARCH_OPTION,
        CREATION_DATE_SEARCH_OPTION,
        MODIFICATION_DATE_SEARCH_OPTION,
        FORM_SEARCH_OPTION,
        ATTACHMENT_SEARCH_OPTION,
        OWNER_SEARCH_OPTION,
        FROM_TEMPLATE_SEARCH_OPTION,
        RECORDS_SEARCH_OPTION,
        INVENTORY_SEARCH_OPTION,
        INVENTORY_PARENT_ID_OPTION,
        INVENTORY_PARENT_TEMPLATE_ID_OPTION,
        INVENTORY_PARENT_SAMPLE_ID_OPTION
      };

  /** List of Search option strings */
  public static final List<String> SEARCH_OPTIONS_LIST =
      Collections.unmodifiableList(Arrays.asList(SEARCH_OPTIONS));

  public static final String WILDCARD = "*";

  public static final byte MAX_TERM_SIZE = 125;

  public static final String NATIVE_LUCENE_PREFIX = "l:";
}
