package com.researchspace.testsandbox;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MyListener {
  // takes spring EL to filter based on type
  @EventListener(condition = "#creationEvent.user.enabled=false")
  public void handleOrderCreatedEvent(CreationEvent creationEvent) {
    System.err.println("1:" + creationEvent.getUser());
  }

  @EventListener(condition = "#creationEvent.user.enabled=true")
  public void handleOrderCreatedEvent2(CreationEvent creationEvent) {
    System.err.println("2:" + creationEvent.getUser());
  }

  @EventListener()
  public void handleOrderCreatedEvent3(CreationEvent creationEvent) {
    System.err.println("3:" + creationEvent.getUser());
  }

  // this is called whenever a subclass event is called
  @EventListener()
  public void handleOrderCreatedEvent4(GeneralEvent general) {
    System.err.println("general event:");
  }
}
