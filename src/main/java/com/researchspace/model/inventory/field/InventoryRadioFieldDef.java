package com.researchspace.model.inventory.field;

import com.researchspace.model.field.ErrorList;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

/** Defines possible options and behaviour of RadioFields in Inventory objects */
@Entity
@Setter
@ToString
public class InventoryRadioFieldDef extends InventoryFieldDef implements Serializable {

  private static final long serialVersionUID = -8400847914215893324L;

  private Long id;

  private String radioOptions;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "inventory_radio_field_def_gen")
  @TableGenerator(
      name = "inventory_radio_field_def_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  /**
   * Get radio options string, as stored in db.
   *
   * <p>Package-scoped for better encapsulation, the code should normally call {@link
   * #getRadioOptionsList()}.
   */
  @Column(columnDefinition = "text")
  @NotBlank
  String getRadioOptions() {
    return radioOptions;
  }

  /**
   * Set radio options string, as stored in db.
   *
   * <p>Package-scoped for better encapsulation, the code should normally call {@link
   * #setRadioOptionsList(List)}.
   */
  void setRadioOptions(String radioOptions) {
    this.radioOptions = radioOptions;
  }

  /** Get radio options string, as stored in db. */
  @Transient
  public String getRadioOptionsDBString() {
    return radioOptions;
  }

  @Transient
  public List<String> getRadioOptionsList() {
    return getOptionListFromString(radioOptions);
  }

  @Transient
  public void setRadioOptionsList(List<String> newOptions) {
    radioOptions = getStringFromOptionList(newOptions);
  }

  public InventoryRadioFieldDef shallowCopy() {
    InventoryRadioFieldDef copy = new InventoryRadioFieldDef();
    copy.setRadioOptions(radioOptions);
    return copy;
  }

  public ErrorList validate(String data) {
    ErrorList el = new ErrorList();

    if (!StringUtils.isEmpty(data) && !getRadioOptionsList().contains(data)) {
      el.addErrorMsgCode("validation.inventoryField.optionsNotAllowed");
    }
    return el;
  }
}
