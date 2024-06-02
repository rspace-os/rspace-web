package com.researchspace.service.impl;

import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.service.Broadcaster;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

/** Stub broadcaster that just logs a communication event. */
@Service("devBroadcaster")
public class DevBroadCaster implements Broadcaster {

  private String source;

  public DevBroadCaster() {}

  public DevBroadCaster(String source) {
    super();
    this.source = source;
  }

  private static Logger log = LogManager.getLogger(DevBroadCaster.class);

  public static Logger getLogger() {
    return log;
  }

  @Override
  public void broadcast(Communication comm) {
    Set<CommunicationTarget> recipients = comm.getRecipients();
    log.info(comm + " sent to " + recipients + "  from " + source);
  }
}
