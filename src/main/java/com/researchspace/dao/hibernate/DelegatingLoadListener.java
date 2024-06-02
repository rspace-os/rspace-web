package com.researchspace.dao.hibernate;

import java.util.List;
import org.hibernate.event.spi.PostLoadEvent;
import org.hibernate.event.spi.PostLoadEventListener;

/**
 * Can only register one post-load listener via Spring - this delegates to a list passein in
 * spring-dao context.ml so we can have more than one listener implementation
 */
public class DelegatingLoadListener implements org.hibernate.event.spi.PostLoadEventListener {

  private List<PostLoadEventListener> delegatedListeners;

  public List<PostLoadEventListener> getDelegatedListeners() {
    return delegatedListeners;
  }

  /**
   * Public setter for Spring
   *
   * @param delegatedListeners
   */
  public void setDelegatedListeners(List<PostLoadEventListener> delegatedListeners) {
    this.delegatedListeners = delegatedListeners;
  }

  /** */
  private static final long serialVersionUID = -2435415482899540624L;

  @Override
  public void onPostLoad(PostLoadEvent event) {
    // delegate to other listeners
    for (PostLoadEventListener delegated : delegatedListeners) {
      delegated.onPostLoad(event);
    }
  }
}
