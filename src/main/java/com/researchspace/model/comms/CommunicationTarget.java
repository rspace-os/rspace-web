package com.researchspace.model.comms;

import com.researchspace.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;
import java.io.Serializable;
import java.util.Date;

/**
 * Stores information about a target's response to a communication; e.g., if they have closed the
 * message. A join table between Communication and User(recipient).
 */
@Entity
@XmlAccessorType(XmlAccessType.NONE)
public class CommunicationTarget implements Serializable {

  private static final long serialVersionUID = -1731053199486945904L;

  private Long id;
  private char type;
  private Date lastStatusUpdate;
  private String lastStatusUpdateMessage;
  private User recipient;
  private Communication communication;
  private CommunicationStatus status;

  /**
   * Creates a new object with status CommunicationStatus.NEW and last status update set to current
   * time.
   */
  public CommunicationTarget() {
    super();
    this.lastStatusUpdate = new Date();
    this.status = CommunicationStatus.NEW;
  }

  @XmlElement
  @Column(length = Communication.MESSAGE_COLUMN_LENGTH)
  public String getLastStatusUpdateMessage() {
    return lastStatusUpdateMessage;
  }

  public void setLastStatusUpdateMessage(String lastStatusUpdateMessage) {
    String newMessage = lastStatusUpdateMessage;
    /* truncating, same as for Communication.setMessage() */
    if (newMessage != null && newMessage.length() > Communication.MESSAGE_COLUMN_LENGTH) {
      newMessage = newMessage.substring(0, Communication.MESSAGE_COLUMN_LENGTH);
    }
    this.lastStatusUpdateMessage = newMessage;
  }

  @Column(length = 1)
  @XmlElement
  private char getType() {
    return type;
  }

  private void setType(char type) {
    this.type = type;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "communication_target_gen")
  @TableGenerator(
      name = "communication_target_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @Temporal(TemporalType.TIMESTAMP)
  @XmlElement
  public Date getLastStatusUpdate() {
    return lastStatusUpdate;
  }

  public void setLastStatusUpdate(Date lastStatusUpdate) {
    this.lastStatusUpdate = lastStatusUpdate;
  }

  @ManyToOne(optional = false)
  @XmlIDREF
  public User getRecipient() {
    return recipient;
  }

  /**
   * For hibernate or testing setup only
   *
   * @param id
   */
  public void setId(Long id) {
    this.id = id;
  }

  public void setRecipient(User recipient) {
    this.recipient = recipient;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((communication == null) ? 0 : communication.hashCode());
    result = prime * result + ((recipient == null) ? 0 : recipient.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CommunicationTarget other = (CommunicationTarget) obj;
    if (communication == null) {
      if (other.communication != null) {
        return false;
      }
    } else if (!communication.equals(other.communication)) {
      return false;
    }
    if (recipient == null) {
      if (other.recipient != null) {
        return false;
      }
    } else if (!recipient.equals(other.recipient)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CommunicationTarget [lastStatusUpdate="
        + lastStatusUpdate
        + ", id="
        + id
        + ", type="
        + type
        + ", lastStatusUpdateMessage="
        + lastStatusUpdateMessage
        + ", recipient="
        + (recipient != null ? recipient.getUsername() : null)
        + ", sender="
        + (communication != null && communication.getOriginator() != null
            ? communication.getOriginator().getUsername()
            : null)
        + ", status="
        + status
        + "]";
  }

  @ManyToOne(optional = false)
  public Communication getCommunication() {
    return communication;
  }

  public void setCommunication(Communication communication) {
    this.communication = communication;
    if (communication != null) { // can be null if removing
      if (communication.isMessageOrRequest()) {
        setType('M');
      } else {
        setType('N');
      }
    }
  }

  @XmlElement
  public CommunicationStatus getStatus() {
    return status;
  }

  public void setStatus(CommunicationStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("status cannot be null.");
    }
    this.status = status;
  }

  @Transient
  public boolean isNew() {
    return CommunicationStatus.NEW.equals(status);
  }

  /**
   * Convenience boolean test for whether this object has {@link CommunicationStatus#COMPLETED}
   *
   * @return
   */
  @Transient
  public boolean isCompleted() {
    return CommunicationStatus.COMPLETED.equals(status);
  }

  /**
   * Convenience boolean test for whether this object has {@link CommunicationStatus#REJECTED}
   *
   * @return
   */
  @Transient
  public boolean isRejected() {
    return CommunicationStatus.REJECTED.equals(status);
  }
}
