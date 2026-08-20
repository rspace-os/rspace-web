package com.researchspace.model.comms;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.abbreviate;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlID;
import jakarta.xml.bind.annotation.XmlIDREF;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.record.BaseRecord;

/**
 * Base class of all communications
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@AuditTrailData(auditDomain = AuditDomain.MESSAGING)
@XmlAccessorType(XmlAccessType.NONE)
public abstract class Communication implements Serializable, UniquelyIdentifiable {

	/**
	 * length of the message column in DB. longer messages will be truncated.
	 */
	public static final int MESSAGE_COLUMN_LENGTH = 2000;

	private static final long serialVersionUID = -1941327130044415401L;

	private Long id;
	private String message;
	private String subject;
	private User originator;
	private Date creationTime;
	private CommunicationPriority priority = CommunicationPriority.REGULAR;
	private BaseRecord record;
	private Set<CommunicationTarget> recipients = new HashSet<>();

	@Override
	@Transient
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.MG, getId());
	}

	@Transient
	@AuditTrailIdentifier()
	@XmlID()
	@XmlAttribute(name = "oid", required = true)
	public String getOidString() {
		return getOid().getIdString();
	}

	static long getSerialversionuid() {
		return serialVersionUID;
	}

	/**
	 * Public constructor
	 */
	public Communication() {
		this.creationTime = new Date();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((creationTime == null) ? 0 : creationTime.hashCode());
		result = prime * result + ((message == null) ? 0 : message.hashCode());
		result = prime * result + ((originator == null) ? 0 : originator.hashCode());
		result = prime * result + ((record == null) ? 0 : record.hashCode());
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
		Communication other = (Communication) obj;
		if (creationTime == null) {
			if (other.creationTime != null) {
				return false;
			}
		} else if (!creationTime.equals(other.creationTime)) {
			return false;
		}

		if (originator == null) {
			if (other.originator != null) {
				return false;
			}
		} else if (!originator.equals(other.originator)) {
			return false;
		}
		if (record == null) {
			if (other.record != null) {
				return false;
			}
		} else if (!record.equals(other.record)) {
			return false;
		}
		if (message == null) {
			if (other.message != null) {
				return false;
			}
		} else if (!message.equals(other.message)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "Communication [message=" + message + ", originator=" + originator + ", creationTime=" + creationTime
				+ ", priority=" + priority + "]";
	}

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "comm_gen")
	@TableGenerator(name = "comm_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val",
			pkColumnValue = "Communication", allocationSize = 1)
	@XmlAttribute(required = true)
	public Long getId() {
		return id;
	}

	// for hibernate
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * A human-created message associated with this communication.
	 * 
	 * @return
	 */
	@XmlElement
	@Column(length = MESSAGE_COLUMN_LENGTH)
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = abbreviate(message, MESSAGE_COLUMN_LENGTH);
	}

	/**
	 * User created subject for communication
	 * 
	 * @return
	 */
	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	/**
	 * The user whose actions led to the communication being created, either
	 * directly, or indirectly ( in the case of a notification) .
	 * 
	 * @return
	 */
	@ManyToOne(optional = false)
	@AuditTrailProperty(name = "originator", properties = { "username", "fullName" })
	@XmlIDREF
	public User getOriginator() {
		return originator;
	}

	public void setOriginator(User originator) {
		this.originator = originator;
	}

	/**
	 * The date/time this message was created.
	 * 
	 * @return
	 */
	@Temporal(TemporalType.TIMESTAMP)
	@Column(nullable = false)
	@XmlElement
	public Date getCreationTime() {
		return creationTime;
	}

	/*
	 * not public, just for hibernate - probably will set this in constructor
	 */
	void setCreationTime(Date creationTime) {
		this.creationTime = creationTime;
	}

	/**
	 * Gets the communication priority
	 * 
	 * @return
	 */
	@XmlElement
	public CommunicationPriority getPriority() {
		return priority;
	}

	public void setPriority(CommunicationPriority priority) {
		this.priority = priority;
	}

	/**
	 * Get a record associated with this message, can be null
	 * 
	 * @return
	 */
	@OneToOne
	public BaseRecord getRecord() {
		return record;
	}

	public void setRecord(BaseRecord record) {
		this.record = record;
	}

	/**
	 * Get a list of recipients of the communication
	 * 
	 * @return
	 */
	@OneToMany(mappedBy = "communication", cascade = { CascadeType.ALL }, orphanRemoval = true)
	@AuditTrailProperty(name = "recipients", properties = "recipient.username")
	@XmlElementWrapper(name = "recipients")
	@XmlElement(name = "recipient")
	public Set<CommunicationTarget> getRecipients() {
		return recipients;
	}

	/**
	 * Get a list of recipients of the communication
	 * 
	 * @return
	 */
	@Transient
	public Set<User> getRecipientsAsUsers() {
		return recipients.stream().map(ct -> ct.getRecipient()).collect(toCollection(() -> new HashSet<>()));
	}

	public void setRecipients(Set<CommunicationTarget> recipients) {
		this.recipients = recipients;
	}

	@Transient
	public boolean isMessageOrRequest() {
		return false;
	}

	@Transient
	public boolean isNotification() {
		return false;
	}
	/**
	 * Adds a recipient to an existing communication, setting bidirectional relations
	 * @param recipient
	 * @return <code>true</code> if added
	 */
	public boolean addRecipient (CommunicationTarget recipient) {
		recipient.setCommunication(this);
		return recipients.add(recipient);
	}
	
	/**
	 * Removes a recipient from  an existing communication, unsetting bidirectional relations
	 * @param toRemove
	 * @return<code>true</code> if removed
	 */
	public boolean removeRecipient (CommunicationTarget toRemove) {
		//RA order important as Comm is part of equals of ct, if null won't be removed from set.
		boolean removed =  recipients.remove(toRemove);
		toRemove.setCommunication(null);
		return removed;
	}
}
