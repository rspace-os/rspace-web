package com.researchspace.model.dtos;

import java.util.List;

/**
 * The fields of the form being edited, as the form field editor reads them, and the revision of
 * that field list. The editor sends the revision back with its save, so a save made from an
 * out-of-date copy is rejected instead of overwriting a newer one.
 *
 * @param fields the form's active fields, in order
 * @param version the revision of this field list; every save that adds, deletes, moves or changes a
 *     field advances it
 */
public record FormFieldEditorFields(List<FormFieldEditorItem> fields, long version) {}
