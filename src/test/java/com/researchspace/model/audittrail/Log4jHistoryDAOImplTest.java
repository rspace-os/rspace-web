package com.researchspace.model.audittrail;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import com.researchspace.model.core.Person;


public class Log4jHistoryDAOImplTest {
	Log4jHistoryDAOImpl log4jDao;
	Person user = TestFactory.createAnyUser("any");
	@BeforeEach
	public void setUp() {
		 log4jDao = new Log4jHistoryDAOImpl();
	}

	@Test
	public void testSaveHistoricData() {
		HistoricData data = createHistoricData();
		log4jDao.save(data);
	}

	private HistoricData createHistoricData() {
		AuditData auditdata = new AuditData();
		auditdata.put("id", 1L);
    return new HistoricData(AuditDomain.COMMUNITY, AuditAction.DUPLICATE,
				user.getFullName(), auditdata, user.getUniqueName());
	}

	@Test
	public void testSaveIterableOfHistoricData() {
		List <HistoricData>data = new ArrayList<>();
		data.add(createHistoricData());
		log4jDao.save(data);
	}

}
