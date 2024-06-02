package com.axiope.dao.hibernate.audit;

import java.util.HashSet;
import java.util.Set;
import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.envers.event.spi.EnversPostDeleteEventListenerImpl;
import org.hibernate.event.spi.PostDeleteEvent;

public class RSEnversPostDeleteListener extends EnversPostDeleteEventListenerImpl {
  private static final long serialVersionUID = 1L;
  private final Set<ObjectAuditFilter> filters = new HashSet<>();
  private final FilterProcessor fp = new RejectIfOneFilterRejectsFilterProcessor();
  private final RSEnversUtils utils = new RSEnversUtils();

  protected RSEnversPostDeleteListener(EnversService enversConfiguration) {
    super(enversConfiguration);
    utils.initFilters(filters);
  }

  @Override
  public void onPostDelete(PostDeleteEvent event) {
    Object entity = event.getEntity();
    if (fp.process(filters, entity)) {
      utils.debug(entity, "deleted");
      super.onPostDelete(event);
    }
  }
}
