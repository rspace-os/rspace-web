package com.researchspace.model.dtos;

import com.researchspace.core.util.PaginationObject;
import com.researchspace.model.record.RSForm;
import java.util.List;
import lombok.Data;

@Data
public class FormMenu {

  private List<RSForm> forms;
  private List<RSForm> menuToAdd;
  private List<PaginationObject> formsForCreateMenuPagination;

  public FormMenu() {
    super();
  }

  public FormMenu(
      List<RSForm> templates,
      List<RSForm> menuToAdd,
      List<PaginationObject> formsForCreateMenuPagination) {
    super();
    setForms(templates);
    setMenuToAdd(menuToAdd);
    setFormsForCreateMenuPagination(formsForCreateMenuPagination);
  }
}
