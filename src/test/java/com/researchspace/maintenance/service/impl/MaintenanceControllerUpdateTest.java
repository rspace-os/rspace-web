package com.researchspace.maintenance.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.ScheduledMaintenanceController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MaintenanceControllerUpdateTest {
  @Mock MaintenanceManager maintenanceManager;
  @Mock UserManager userManager;
  @InjectMocks ScheduledMaintenanceController controller;

  @Test
  void missingWindowReturns404() throws Exception {
    when(maintenanceManager.updateScheduledMaintenance(any(), any()))
        .thenThrow(new ObjectRetrievalFailureException(ScheduledMaintenance.class, 42L));
    MockMvcBuilders.standaloneSetup(controller)
        .build()
        .perform(
            post("/system/maintenance/ajax/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"id\":42,\"startDate\":\"2031-01-01T10:00:00.000Z\",\"endDate\":\"2031-01-01T12:00:00.000Z\",\"message\":\"stale\"}"))
        .andExpect(status().isNotFound());
  }
}
