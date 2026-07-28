package com.researchspace.model.comms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlIDREF;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.audittrail.AuditTrailProperty;

/**
 * Adds a linked list capability to connect a sequence of communications. These
 * communications are user initiated.
 * 
 * 
 */
@Entity
@XmlAccessorType(XmlAccessType.NONE)
public class MessageOrRequest extends Communication implements Serializable, Comparable<MessageOrRequest> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7431872984297118912L;
	private MessageOrRequest previous;
	private MessageOrRequest next;
	private CommunicationStatus status;
	private Date terminationTime;
	private MessageType messageType;
	private Date requestedCompletionDate;
	private boolean isLatest;

	/**
	 * For Spring/hibernate. CLients should use <br/>
	 * <code>public MessageOrRequest (MessageType messageType)</code>
	 */
	public MessageOrRequest() {
		super();
		this.status = CommunicationStatus.NEW;

	}

	/**
	 * Creates a new object, setting its creation time and the status to
	 * {@link CommunicationStatus#NEW}
	 */
	public MessageOrRequest(MessageType messageType) {
		this();
		this.messageType = messageType;

	}

	@XmlElement
	public CommunicationStatus getStatus() {
		return status;
	}

	public void setStatus(CommunicationStatus status) {
		this.status = status;
	}

	public void setLatest(boolean isLatest) {
		if (next != null && isLatest) {
			throw new IllegalStateException("Cannot be latest when has a next message");
		}
		this.isLatest = isLatest;
	}

	@Temporal(TemporalType.TIMESTAMP)
	@XmlElement
	public Date getRequestedCompletionDate() {
		return requestedCompletionDate;
	}

	public void setRequestedCompletionDate(Date requestedCompletionDate) {
		this.requestedCompletionDate = requestedCompletionDate;
	}

	/**
	 * Lazy-loaded to avoid loading whole list of requests
	 * 
	 * @return
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@XmlIDREF
	public MessageOrRequest getPrevious() {
		return previous;
	}

	/**
	 * Sets the previous message in the chain, which must be created before this
	 * message
	 * 
	 * @param previous
	 */
	public void setPreviousMessage(MessageOrRequest previous) {
		if (previous != null && previous.getCreationTime().after(this.getCreationTime())) {
			// if next's creation date is after this creation date.
			throw new IllegalArgumentException("Cannot set previous message to be later than this message");
		}
		setPrevious(previous);
	}

	/*
	 * For hibernate; hibernate blows up if enforcing correct timing
	 * 
	 * @param previous
	 */
	void setPrevious(MessageOrRequest previous) {
		this.previous = previous;
	}

	/**
	 * Lazy-loaded to avoid loading whole list of requests
	 * 
	 * @return
	 */
	@OneToOne(fetch = FetchType.LAZY)
	@XmlIDREF
	public MessageOrRequest getNext() {
		return next;
	}

	/**
	 * Sets the next message in the chain, which must be created after this
	 * message
	 * 
	 * @param next
	 */
	public void setNextMessage(MessageOrRequest next) {
		if (next != null && next.getCreationTime().before(this.getCreationTime())) {
			// if next's creation date is after this creation date
			throw new IllegalArgumentException("Cannot set next message to be earlier than this message");
		}
		setNext(next);
	}

	/*
	 * For hibernate only. clients use setNextMessage()
	 */
	void setNext(MessageOrRequest next) {
		this.next = next;
	}

	@Temporal(TemporalType.TIMESTAMP)
	@XmlElement
	public Date getTerminationTime() {
		return terminationTime;
	}

	public void setTerminationTime(Date terminationTime) {
		this.terminationTime = terminationTime;
	}

	@Column(nullable = false)
	@AuditTrailProperty(name = "type")
	@XmlElement
	public MessageType getMessageType() {
		return messageType;
	}

	/*
	 * For hibernate
	 */
	void setMessageType(MessageType messageType) {
		this.messageType = messageType;
	}

	@Transient
	public boolean isTerminated() {
		return CommunicationStatus.CANCELLED.equals(status) || CommunicationStatus.COMPLETED.equals(status)
				|| CommunicationStatus.REJECTED.equals(status);
	}

	@Transient
	public boolean isSimpleMessage() {
		return MessageType.SIMPLE_MESSAGE.equals(messageType) || MessageType.GLOBAL_MESSAGE.equals(messageType);
	}

	@Transient
	public boolean isStatefulRequest() {
		return !(MessageType.SIMPLE_MESSAGE.equals(messageType) || MessageType.GLOBAL_MESSAGE.equals(messageType));
	}

	/**
	 * Boolean test for whether this message has a next message
	 * 
	 * @return
	 */
	@Transient
	public boolean isHasNextMessage() {
		return next != null;
	}

	/**
	 * Boolean test for whether this message has a previous message
	 * 
	 * @return
	 */
	@Transient
	public boolean isHasPreviousMessage() {
		return previous != null;
	}

	/**
	 * Boolean flag to indicate the most recent message, so as to avoid
	 * traversing the list each time to find the most recent message.
	 * 
	 * @return
	 */
	@XmlAttribute
	public boolean isLatest() {
		return isLatest;
	}

	@Transient
	public boolean isMessageOrRequest() {
		return true;
	}

	@Transient
	public boolean isNew() {
		return CommunicationStatus.NEW.equals(status);
	}

	@Transient
	public boolean isCompleted() {
		return CommunicationStatus.COMPLETED.equals(status);
	}

	/**
	 * Orders is consistent with equals and hashcode.
	 */
	@Override
	public int compareTo(MessageOrRequest other) {
		int rc = this.getCreationTime().compareTo(other.getCreationTime());
		if (rc != 0) {
			return rc;
		}
		rc = this.getOriginator().compareTo(other.getOriginator());
		if (rc != 0) {
			return rc;
		}
		return this.getMessage().compareTo(other.getMessage());

	}

	public static List<MessageOrRequestView> toView(ISearchResults<MessageOrRequest> messages) {
		List<MessageOrRequestView> rc = new ArrayList<>();
		for (MessageOrRequest msg : messages.getResults()) {
			MessageOrRequestView view = new MessageOrRequestView();
			view.setCreationTime(msg.getCreationTime());
			view.setId(msg.getId());
			view.setMessage(msg.getMessage());
			view.setMessageType(msg.getMessageType());
			view.setOriginator(msg.getOriginator().toPublicInfo());
			if (msg.getRecord() != null) {
				view.setRecordName(msg.getRecord().getName());
			}
			if (msg.getMessageType().equals(MessageType.REQUEST_CREATE_LAB_GROUP)) {
				view.setGroupName(((CreateGroupMessageOrRequest) msg).getGroupName());
			} else if (msg.getMessageType().equals(MessageType.REQUEST_JOIN_LAB_GROUP)
					|| msg.getMessageType().equals(MessageType.REQUEST_JOIN_PROJECT_GROUP) ) {
				view.setGroupName(((GroupMessageOrRequest) msg).getGroup().getDisplayName());
				view.setGroupType(((GroupMessageOrRequest) msg).getGroup().getGroupType());
			}
			rc.add(view);
		}
		return rc;
	}

}
