package com.axiope.dao.hibernate.audit;

import java.util.HashSet;
import java.util.Set;
import org.hibernate.envers.boot.internal.EnversService;
import org.hibernate.envers.event.spi.EnversPostUpdateEventListenerImpl;
import org.hibernate.event.spi.PostUpdateEvent;

public class RSEnversPostUpdateListener extends EnversPostUpdateEventListenerImpl {
  private static final long serialVersionUID = 1L;
  private final Set<ObjectAuditFilter> filters = new HashSet<>();
  private final FilterProcessor fp = new RejectIfOneFilterRejectsFilterProcessor();
  private final RSEnversUtils utils = new RSEnversUtils();

  protected RSEnversPostUpdateListener(EnversService enversConfiguration) {
    super(enversConfiguration);
    utils.initFilters(filters);
  }

  @Override
  public void onPostUpdate(PostUpdateEvent event) {
    Object entity = event.getEntity();
    if (fp.process(filters, entity)) {
      utils.debug(entity, "updated");
      super.onPostUpdate(event);
    }
  }
}
