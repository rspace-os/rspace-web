package com.researchspace.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.model.record.BaseRecord;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class RecordGroupSharingTest {

  private User user;
  @Mock private BaseRecord record;

  @BeforeEach
  public void setUp() {
    openMocks(this);
    user = new User(RecordGroupSharing.ANONYMOUS_USER);
    when(record.getId()).thenReturn(1L);
  }

  @Test
  void creationDateIsEncapsulated() {
    RecordGroupSharing rgs = new RecordGroupSharing();
    Date date = new Date();
    rgs.setCreationDate(date);
    assertFalse(date == rgs.getCreationDate(), "RGS should store its own date");
    date.setTime(1L);
    assertFalse(rgs.getCreationDate().getTime() == date.getTime());
  }

  @Test
  void shouldCreatePublicLinkForAnonymousUser() {
    RecordGroupSharing rgs = new RecordGroupSharing(user, record);
    assertNotNull(rgs.getPublicLink());
  }

  @Test
  void shouldNotCreatePublicLinkForNonAnonymousUser() {
    user = new User("not anonymous");
    RecordGroupSharing rgs = new RecordGroupSharing(user, record);
    assertNull(rgs.getPublicLink());
  }
}
