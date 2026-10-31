package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResourceAccessAuditTrailTest {
  @Test
  void escapesDelimitersAndBackslashesInEveryUntrustedField() {
    AuditTrailService sink = mock(AuditTrailService.class);
    User subject = new User();
    subject.setUsername("sub;=\\ject");
    new ResourceAccessAuditTrail(sink)
        .accessChanged(
            new ResourceAccessAuditDelta(
                new Object(),
                subject,
                subject,
                AuditAction.WRITE,
                "user;=\\12",
                "name;=\\x",
                "old;=\\x",
                "new;=\\x",
                Instant.EPOCH,
                ResourceAccessAuditReason.ASSIGNMENT_CHANGE));
    var captured = ArgumentCaptor.forClass(GenericEvent.class);
    verify(sink).notify(captured.capture());
    assertEquals(
        "reason=ASSIGNMENT_CHANGE;grantee=user\\;\\=\\\\12;name=name\\;\\=\\\\x;oldRole=old\\;\\=\\\\x;newRole=new\\;\\=\\\\x;subject=sub\\;\\=\\\\ject",
        captured.getValue().getDescription());
  }
}
