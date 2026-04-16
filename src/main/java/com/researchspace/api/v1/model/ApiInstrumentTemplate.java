package com.researchspace.api.v1.model;

import com.researchspace.model.inventory.InstrumentTemplate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApiInstrumentTemplate extends ApiInstrumentEntity {

  /** default constructor used by jackson deserializer */
  public ApiInstrumentTemplate() {
    super();
    setType(ApiInventoryRecordType.INSTRUMENT_TEMPLATE);
    super.setTemplate(true);
    super.setCanBeDeleted(true);
  }

  public ApiInstrumentTemplate(InstrumentTemplate instrumentTemplate) {
    super(instrumentTemplate);
    super.setCanBeDeleted(true); // always true for now
  }
}
