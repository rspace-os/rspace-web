package com.researchspace.archive.model;

import com.researchspace.model.comms.MessageOrRequest;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

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
