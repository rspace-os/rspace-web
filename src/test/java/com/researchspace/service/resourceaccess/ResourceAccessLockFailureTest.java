package com.researchspace.service.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.resourceaccess.ResourceAccessDao;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.MessageSourceUtils;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ResourceAccessLockFailureTest {
  static Stream<org.junit.jupiter.params.provider.Arguments> failures() {
    return Stream.of(1020, 1213, 1205)
        .flatMap(
            code ->
                Stream.of(false, true)
                    .flatMap(
                        translated ->
                            Stream.of("facts", "lock", "flush")
                                .map(
                                    stage ->
                                        org.junit.jupiter.params.provider.Arguments.of(
                                            code, translated, stage))));
  }

  @ParameterizedTest
  @MethodSource("failures")
  void mapsConcurrentLockFailuresToStale(int code, boolean translated, String stage) {
    ResourceAccessDao dao = mock(ResourceAccessDao.class);
    var scheme = new ResourceAccessTestScheme();
    var registry = new ResourceRoleSchemeRegistry(List.of(scheme));
    var manager =
        new ResourceAccessManagerImpl(
            dao,
            registry,
            new ResourceAccessResolver(registry),
            mock(ResourceAccessDirectoryPolicy.class),
            mock(MessageSourceUtils.class),
            event -> {});
    User subject = new User();
    subject.setId(12L);
    subject.setUsername("subject");
    subject.setFirstName("Subject");
    subject.setLastName("User");
    subject.setEnabled(true);
    ResourceAccess access = new ResourceAccess(scheme.key(), subject, new Date());
    access.setId(1L);
    SQLException sql = new SQLException("concurrent update", "40001", code);
    RuntimeException failure =
        translated
            ? new PessimisticLockingFailureException("translated", sql)
            : new RuntimeException(sql);
    @SuppressWarnings("unchecked")
    ProtectedResourceAccess<ResourceAccess, Long> resource = mock(ProtectedResourceAccess.class);
    when(resource.featureEnabled(subject)).thenReturn(true);
    if (stage.equals("lock")) {
      when(resource.lock(1L)).thenThrow(failure);
    } else {
      User other = new User();
      other.setId(13L);
      other.setUsername("other");
      access.addAssignment(ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, subject));
      access.addAssignment(ResourceRoleAssignment.forUser(ResourceAccessTestScheme.OWNER, other));
      when(resource.lock(1L)).thenReturn(Optional.of(access));
      when(resource.access(access)).thenReturn(access);
      if (stage.equals("facts")) {
        when(dao.lockAuthorizationFacts(access, subject)).thenThrow(failure);
      } else {
        when(dao.lockAuthorizationFacts(access, subject))
            .thenReturn(
                new ResourceAccessDao.AuthorizationFacts(subject, true, Set.of(), Set.of()));
        doThrow(failure).when(dao).flush();
      }
    }
    assertEquals(
        ResourceAccessException.Reason.STALE,
        assertThrows(
                ResourceAccessException.class,
                () ->
                    manager.removeSelf(
                        resource, new RemoveSelfResourceAccess<>(1L), subject, subject))
            .reason());
  }
}
