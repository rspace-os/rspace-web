package com.researchspace.model.audittrail;

import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import com.researchspace.model.core.Person;

/**
 * Logs events that should be added to the audit trail.
 */

public class AuditTrailImpl implements AuditTrailService {

	private HistoryDAO historyDao;
	Logger log = LoggerFactory.getLogger(AuditTrailImpl.class);
	
	private boolean active = true;

	public void setHistoryDao(HistoryDAO historyDao) {
		this.historyDao = historyDao;
	}
	
	private UnaryOperator<Person> operateAsUserLookup = UnaryOperator.identity();

	public void setUserLookup(UnaryOperator<Person> userLookup) {
		this.operateAsUserLookup = userLookup;
	}

	@Override
	@EventListener()
	public void notify(HistoricalEvent auditEvent) {
		if (active) {
			AuditTrailHistoricalEventVisitor visitor = new AuditTrailHistoricalEventVisitor();
			auditEvent.accept(visitor);
			for (HistoricData data: visitor.getHistoryData()) {
				Person subject = auditEvent.getSubject();
				Person operatorPerson = operateAsUserLookup.apply(subject);
				if (!operatorPerson.getUniqueName().equals(subject.getUniqueName())) {
					data.setFullName(operatorPerson.getFullName() + " -> " + subject.getFullName());
				    data.setSubject(operatorPerson.getUniqueName()+"->" + subject.getUniqueName());
				}		
			}
			historyDao.save(visitor.getHistoryData());
		}
	}

	@Override
	public void setActive(boolean active) {
		logChange(active);
		this.active = active;
	}

	private void logChange(boolean active) {
		if (!this.active && active) {
			log.info("Switching on logging.");
		} else if (this.active && !active) {
			log.info("Switching  off logging.");
		}
	}

}
