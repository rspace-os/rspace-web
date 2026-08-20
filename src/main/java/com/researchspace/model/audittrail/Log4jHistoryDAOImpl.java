package com.researchspace.model.audittrail;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists audit data using a string format to Log4j.
 */
public class Log4jHistoryDAOImpl implements HistoryDAO {

	public static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

	@Override
	public HistoricData save(HistoricData data) {
		StringBuilder sb = new StringBuilder();
		sb.append("domain:").append(data.getDomain()).append(" action:").append(data.getAction())
        .append(" ");
		if (data.getData() != null) {
			sb.append("[").append(data.getData().toJson()).append("]");
		}
		sb.append(" ").append(data.getSubject());
		sb.append("(").append(data.getFullName()).append(")");
		if(data.getDescription() != null){
			sb.append("description:[").append(data.getDescription()).append("]");
		}
		log.info(sb.toString());
		return data;
	}

	@Override
	public void save(Iterable<HistoricData> data) {
		if (data == null){
			return;
		}
    for (HistoricData datum : data) {
      save(datum);
    }
	}

}
