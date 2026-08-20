package com.researchspace.model.audittrail;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.apache.commons.lang3.StringUtils;

public class AuditTrailHistoricalEventVisitor extends AuditEventVisitor {

	private List<HistoricData> historyData = new ArrayList<>();

	AuditTrailData getAuditDomain(Object object) {
		if (object == null) {
			return null;
		}
		Class<?> clazz = getClass(object);
    return clazz.getAnnotation(AuditTrailData.class);
	}

	private Class<?> getClass(Object object) {
		if (object instanceof Class) {
			return (Class) object;
		} else {
			return object.getClass();
		}
	}

	@Override
	public void visit(AuditSearchEvent searchEvent) {
		Object srchCfg = searchEvent.getAuditedObject();
		processEvent(searchEvent, srchCfg);
	}

	@Override
	public void visit(SigningEvent signingEvent) {
		Object signed = signingEvent.getAuditedObject();
		processEvent(signingEvent, signed);
	}

	/**
	 * Currently only handles a single object being restored at once.
	 */
	public void visit(RestoreEvent restoreEvent) {
		Object restored = restoreEvent.getAuditedObject();
		processEvent(restoreEvent, restored);
		// currently only ha
		for (HistoricData data : historyData) {
			data.getData().put("restoreType", restoreEvent.getRestoreType());
			if (RestoreType.REVISION.equals(restoreEvent.getRestoreType())) {
				data.getData().put("revision", restoreEvent.getRevision());
			}
		}
	}

  @Override
  public void visit(DuplicateAuditEvent originalDuplicateEvent) {
    Map<?, ?> map = originalDuplicateEvent.getOriginalToCopy();
    for (Entry<?, ?> entry : map.entrySet()) {
      Object copy = entry.getValue();
      AuditData data = originalDuplicateEvent.getAuditData(entry.getKey(), copy);
      HistoricData hData = new HistoricData(getAuditDomain(copy).auditDomain(),
          originalDuplicateEvent, data);
      // be sure that ID and name are recursively referring to the right object
      setRecursiveAuditParameters(data, hData);
      this.historyData.add(hData);
    }
  }

  private static void setRecursiveAuditParameters(AuditData data, HistoricData hData) {
    AuditData auditFrom = (AuditData) data.get("from");
    AuditData auditTo = (AuditData) data.get("to");
    if (auditFrom != null && auditTo != null &&
        auditFrom.get("id") != null && auditFrom.get("name") != null &&
        auditTo.get("name") != null) {
      data.put("id", auditFrom.get("id"));
      data.put("name", auditFrom.get("name"));
      hData.setDescription(
          "from: \"" + auditFrom.get("name") + "\" to: \"" + auditTo.get("name") + "\"");
    }
  }

  @Override
  public void visit(MoveAuditEvent moveEvent) {
    Object moved = moveEvent.getAuditedObject();
    processEvent(moveEvent, moved);
  }

	@Override
	public void visit(RenameAuditEvent renameEvent) {
		Object renamed = renameEvent.getAuditedObject();
		processEvent(renameEvent, renamed);
	}

	@Override
	public void visit(CreateAuditEvent createEvent) {
		Object create = createEvent.getAuditedObject();
		processEvent(createEvent, create);
	}

	private void processEvent(HistoricalEvent event, Object domainObject) {
		validateArgs(event);
		AuditTrailData auditDataAnnot = getAuditDomain(domainObject);
		AuditDomain domain = AuditDomain.UNKNOWN;// default
		if (auditDataAnnot == null) {
			// default case if nothing set
			AuditData data = event.getAuditData();
			HistoricData hData = new HistoricData(domain, event, data);
			this.historyData.add(hData);
		} else if (!StringUtils.isBlank(auditDataAnnot.delegateToCollection())) {
			BeanUtilsBean bean = new BeanUtilsBean();

			String collectionName = auditDataAnnot.delegateToCollection();
			Object value = null;
			try {
				value = bean.getPropertyUtils().getSimpleProperty(domainObject, collectionName);
			} catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
				e.printStackTrace();
			}
      if (!(value instanceof Iterable)) {
				throw new IllegalStateException("collection must be an iterable");
			}

			Iterable<?> iterable = (Iterable) value;
      for (Object domainObjectInCollection : iterable) {
        AuditTrailData childAnnot = getAuditDomain(domainObjectInCollection);
        if (childAnnot != null) {
          domain = childAnnot.auditDomain();
        }
        AuditData data = event.getAuditData(domainObjectInCollection);
        HistoricData hData = new HistoricData(domain, event, data);
        hData.setDescription(event.getDescription());
        this.historyData.add(hData);
      }
		} else {
			domain = auditDataAnnot.auditDomain();
			AuditData data = event.getAuditData();
			this.historyData.add(new HistoricData(domain, event, data));
		}
	}

	@Override
	public void visit(GenericEvent event) {
		Object audited = event.getAuditedObject();
		processEvent(event, audited);
	}

	@Override
	public void visit(ShareRecordAuditEvent event) {
		Object shared = event.getAuditedObject();
		processEvent(event, shared);
	}

	@Override
	List<HistoricData> getHistoryData() {
		return historyData;
	}

	@Override
	public String toString() {
		return "AuditTrailHistoricalEventVisitor [historyData=" + historyData + "]";
	}

}
