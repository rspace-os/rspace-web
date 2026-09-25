package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeriveOperationTest {

  private static final DeriveOperation DERIVE = new DeriveOperation();

  private static final LabelResolver CATALOG =
      (key, args) -> {
        String pattern =
            "operations.derive.linkFieldName".equals(key)
                ? "Is Derived From using process: {processName}"
                : key;
        for (var arg : args.entrySet()) {
          pattern = pattern.replace("{" + arg.getKey() + "}", String.valueOf(arg.getValue()));
        }
        return pattern;
      };

  private static ApiInventoryOperationRequests.Derive request() {
    ApiInventoryOperationRequests.Derive request = new ApiInventoryOperationRequests.Derive();
    request.setSampleName("Extracted DNA");
    request.setProcessName("PCR");
    request.setCount(new BigDecimal("1"));
    request.setEachAmount(millilitres("0.5"));
    request.setOrigin(requestOrigin(100, millilitres("1")));
    return request;
  }

  @Test
  void namesTheProvenanceLinkAfterTheProcessTheCallerNamed() {
    ApiExtraField link =
        DERIVE
            .build(
                request(), List.of(originState(100, "5")), CATALOG, LocalDate.parse("2026-08-20"))
            .getNewSample()
            .getExtraFields()
            .get(0);

    assertEquals("Is Derived From using process: PCR", link.getName());
    assertEquals("IsDerivedFrom", link.getLink().getRelationType());
    assertEquals("SS100", link.getLink().getTargetGlobalId());
  }
}
