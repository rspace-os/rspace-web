package com.researchspace.maintenance.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.maintenance.dao.MaintenanceDao;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectRetrievalFailureException;

@ExtendWith(MockitoExtension.class)
class MaintenanceUpdateTest {
  @Mock MaintenanceDao dao;
  @Mock User admin;
  @InjectMocks MaintenanceManagerImpl manager;

  @Test
  void copiesEditableFieldsOntoLoadedEntity() {
    when(admin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    ScheduledMaintenance existing = new ScheduledMaintenance(new Date(1000), new Date(2000));
    existing.setId(42L);
    ScheduledMaintenance patch = new ScheduledMaintenance(new Date(3000), new Date(4000));
    patch.setId(42L);
    patch.setStopUserLoginDate(new Date(2500));
    patch.setMessage("updated");
    when(dao.get(42L)).thenReturn(existing);

    assertSame(existing, manager.updateScheduledMaintenance(patch, admin));
    assertEquals(patch, existing);
    verify(dao, never()).save(any());
  }

  @Test
  void missingWindowIsNeverSaved() {
    when(admin.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    ScheduledMaintenance patch = new ScheduledMaintenance(new Date(1000), new Date(2000));
    patch.setId(42L);
    when(dao.get(42L))
        .thenThrow(new ObjectRetrievalFailureException(ScheduledMaintenance.class, 42L));

    assertThrows(
        ObjectRetrievalFailureException.class,
        () -> manager.updateScheduledMaintenance(patch, admin));
    verify(dao, never()).save(any());
  }
}
