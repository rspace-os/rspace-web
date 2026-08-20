package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.researchspace.model.core.Person;

public class AuditTrailImplTest {

	@Mock HistoryDAO historyDao;

	@Captor
	ArgumentCaptor<Iterable<HistoricData>> historicDataCaptor;
	
	AuditTrailImpl service;
	Person subject;
	Person sysadminOperatingAs;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		service = new AuditTrailImpl();
		service.setHistoryDao(historyDao);
		subject = TestFactory.createAnyUser("anySubject");
		sysadminOperatingAs = TestFactory.createAnyUser("sysadminOperatingAs");
	
	}

	@Test
	 void testInitialisation() {	
		service.setActive(false);
		service.notify(new GenericEvent(subject, new Object(),AuditAction.CREATE));
		Mockito.verify(historyDao, never()).save(Mockito.any(List.class));
		service.setActive(true);
		service.notify(new GenericEvent(subject, new Object(),AuditAction.CREATE));
		Mockito.verify(historyDao, atLeastOnce()).save(Mockito.any(List.class));
	}
	
	@Test
	public void testPersonConverter() {	
		service.setActive(true);
		service.setUserLookup(subject -> sysadminOperatingAs);
		
		service.notify(new GenericEvent(subject, new Object(),AuditAction.CREATE));
		Mockito.verify(historyDao).save(historicDataCaptor.capture());
		Iterable<HistoricData> dataIterable = historicDataCaptor.getValue();
		HistoricData data = dataIterable.iterator().next();
		assertEquals(sysadminOperatingAs.getUniqueName() + "->" + subject.getUniqueName(), data.getSubject());
		assertEquals(sysadminOperatingAs.getFullName()+" -> "+subject.getFullName(), data.getFullName());
	}

}
