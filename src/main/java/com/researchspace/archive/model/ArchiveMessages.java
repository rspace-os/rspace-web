package com.researchspace.archive.model;

import com.researchspace.model.comms.MessageOrRequest;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlID;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class ArchiveMessages {

  private List<MessageOrRequest> messages = new ArrayList<MessageOrRequest>();

  public ArchiveMessages() {
    super();
  }

  private List<String> usernames = new ArrayList<String>();

  public void setUsernames(List<String> usernames) {
    this.usernames = usernames;
  }

  @XmlElementWrapper(name = "listOfUsernames")
  @XmlElement(name = "username")
  @XmlID
  public List<String> getUsernames() {
    return usernames;
  }

  @XmlElementWrapper(name = "listOfMessages")
  @XmlElement(name = "message")
  public List<MessageOrRequest> getMessages() {
    return messages;
  }

  public void setMessages(List<MessageOrRequest> messages) {
    this.messages = messages;
  }
}
