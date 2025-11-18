package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.StoichiometryMoleculeManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class StoichiometryInventoryLinkManagerImplTest {

  @Mock private StoichiometryInventoryLinkDao linkDao;
  @Mock private StoichiometryMoleculeManager moleculeManager;
  @Mock private IPermissionUtils elnPerms;
  @Mock private InventoryPermissionUtils invPerms;

  @InjectMocks private StoichiometryInventoryLinkManagerImpl manager;

  private User user;
  private StoichiometryMolecule molecule;
  private Sample invSample;
  private BaseRecord owningRecord;

  @Before
  public void setUp() {
    user = new User();
    user.setUsername("u1");
    molecule = new StoichiometryMolecule();
    molecule.setId(10L);
    invSample = new Sample();
    invSample.setId(200L);
    owningRecord = org.mockito.Mockito.mock(BaseRecord.class);
  }

  @Test
  public void createLinkSuccess() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(10L);
    req.setInventoryItemGlobalId("SA200");
    req.setQuantityUsed(2.5);

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    when(invPerms.assertUserCanEditInventoryRecord(
            any(GlobalIdentifier.class), org.mockito.Mockito.eq(user)))
        .thenReturn(invSample);

    when(linkDao.save(any(StoichiometryInventoryLink.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StoichiometryInventoryLinkDTO dto = manager.createLink(req, user);
    assertEquals(Long.valueOf(10L), dto.getStoichiometryMoleculeId());
    assertEquals("SA200", dto.getInventoryItemGlobalId());
    assertEquals(Double.valueOf(2.5), dto.getQuantityUsed());
  }

  @Test
  public void createLinkDeniedOnElnPermissions() {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(10L);
    req.setInventoryItemGlobalId("SA200");
    req.setQuantityUsed(1.0);

    when(moleculeManager.getById(10L)).thenReturn(molecule);
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(false);

    assertThrows(NotFoundException.class, () -> manager.createLink(req, user));
  }

  @Test
  public void updateQuantitySuccess() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);

    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doNothing()
        .when(invPerms)
        .assertUserCanEditInventoryRecord(existing.getInventoryRecord(), user);
    when(linkDao.save(any(StoichiometryInventoryLink.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StoichiometryInventoryLinkDTO dto = manager.updateQuantity(123L, 7.0, user);
    assertEquals(Long.valueOf(123L), dto.getId());
    assertEquals(Double.valueOf(7.0), dto.getQuantityUsed());
  }

  @Test
  public void getByIdDeniedOnInventoryPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);

    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.READ, user)).thenReturn(true);
    doThrow(new NotFoundException())
        .when(invPerms)
        .assertUserCanEditInventoryRecord(invSample, user);

    assertThrows(NotFoundException.class, () -> manager.getById(123L, user));
  }

  @Test
  public void deleteSuccess() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);

    doNothing().when(invPerms).assertUserCanEditInventoryRecord(invSample, user);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    manager.deleteLink(existing.getId(), user);
    verify(linkDao).remove(123L);
  }

  @Test
  public void deleteDeniedOnInventoryPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    when(moleculeManager.getDocContainingMolecule(molecule)).thenReturn(owningRecord);
    when(elnPerms.isPermitted(owningRecord, PermissionType.WRITE, user)).thenReturn(true);
    doThrow(new NotFoundException())
        .when(invPerms)
        .assertUserCanEditInventoryRecord(invSample, user);
    assertThrows(NotFoundException.class, () -> manager.deleteLink(existing.getId(), user));
  }

  @Test
  public void deleteDeniedOnElnPermissions() {
    StoichiometryInventoryLink existing = new StoichiometryInventoryLink();
    existing.setId(123L);
    existing.setStoichiometryMolecule(molecule);
    existing.setSample(invSample);
    when(linkDao.getSafeNull(123L)).thenReturn(java.util.Optional.of(existing));
    assertThrows(NotFoundException.class, () -> manager.deleteLink(existing.getId(), user));
  }

  @Test
  public void getByIdMissingEntityThrowsNotFound() {
    when(linkDao.getSafeNull(999L)).thenReturn(java.util.Optional.empty());
    assertThrows(NotFoundException.class, () -> manager.getById(999L, user));
  }
}
