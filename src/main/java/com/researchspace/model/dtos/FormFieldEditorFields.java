package com.researchspace.model.dtos;

import java.util.List;

/**
 * The fields of the form being edited, as the form field editor reads them, and the version of that
 * field list. The editor sends the version back with its save, so a save made from an out-of-date
 * copy is rejected instead of overwriting a newer one.
 *
 * @param fields the form's active fields, in order
 * @param version identifies this exact field list; it changes whenever a field is added, deleted,
 *     moved or changed
 */
public record FormFieldEditorFields(List<FormFieldEditorItem> fields, String version) {}
