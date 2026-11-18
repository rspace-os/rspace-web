package com.researchspace.service;

import com.researchspace.model.dtos.FormFieldSaveItem;
import com.researchspace.model.record.FormType;
import java.util.List;

/**
 * A whole form definition from the forms API: its name, tags and type, and its full ordered field
 * list. Field ids are the ids of the form the client read (the original, never a draft).
 *
 * @param name the form name
 * @param tags the form tags, possibly null
 * @param formType the form type; only used on create
 * @param fields every field, in order; saved fields the list omits are deleted
 */
public record FormDefinitionCommand(
    String name, String tags, FormType formType, List<FormFieldSaveItem> fields) {

  public FormDefinitionCommand {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
