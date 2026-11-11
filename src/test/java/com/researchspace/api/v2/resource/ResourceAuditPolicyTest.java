package com.researchspace.api.v2.resource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceAuditPolicyTest {
  @Test
  void rejectsMissingMetadataButAllowsInheritedMetadataAndNoPolicy() {
    assertThrows(IllegalArgumentException.class, () -> catalog(Plain.class, true));
    assertThrows(IllegalArgumentException.class, () -> catalog(MissingIdentifier.class, true));
    assertThrows(IllegalArgumentException.class, () -> catalog(DuplicateIdentifier.class, true));
    assertNotNull(catalog(Inherited.class, true));
    assertNotNull(catalog(Plain.class, false));
  }

  @SuppressWarnings("unchecked")
  private static <T> ApiV2ResourceCatalog catalog(Class<T> type, boolean audit) {
    ResourceOperations<T, Long> operations = mock(ResourceOperations.class);
    var description =
        new CollectionDescription<>(
            "things",
            type,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), entity -> 1L)),
            "id",
            List.of(new Sort("id", true)));
    var builder = ApiV2ResourceSpec.readOnly(description, operations, Long::valueOf);
    if (audit) builder.audit(ResourceAuditPolicy.standard(operations));
    return new ApiV2ResourceCatalog(List.of(builder.build()));
  }

  public static class Plain {}

  @AuditTrailData(auditDomain = AuditDomain.RECORD)
  public static class MissingIdentifier {}

  @AuditTrailData(auditDomain = AuditDomain.RECORD)
  public static class Audited {
    @AuditTrailIdentifier
    public String auditId() {
      return "thing:1";
    }
  }

  public static class Inherited extends Audited {}

  public static class DuplicateIdentifier extends Audited {
    @AuditTrailIdentifier
    public String otherId() {
      return "thing:2";
    }
  }
}
