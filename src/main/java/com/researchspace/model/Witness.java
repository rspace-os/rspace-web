package com.researchspace.model;

import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Witness implements Serializable  {

	private static final long serialVersionUID = 1547737164563289458L;

	private Long id;
	private String optionString;
	private User witness;
	private Date witnessesDate;
	private Signature signature;
	private boolean witnessed = false;

	@Override
	public String toString() {
		return "Witness [id=" + id + ", option=" + optionString + ", witness=" + witness + ", witnessesDate="
				+ witnessesDate + ", signature=" + signature + ", witnessed=" + witnessed + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((witness == null) ? 0 : witness.hashCode());
		result = prime * result + ((witnessesDate == null) ? 0 : witnessesDate.hashCode());
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
		Witness other = (Witness) obj;
		if (witness == null) {
			if (other.witness != null) {
				return false;
			}
		} else if (!witness.equals(other.witness)) {
			return false;
		}
		if (witnessesDate == null) {
			if (other.witnessesDate != null) {
				return false;
			}
		} else if (!witnessesDate.equals(other.witnessesDate)) {
			return false;
		}
		return true;
	}

	/**
	 * Public constructor
	 * 
	 * @param witness
	 */
	public Witness(User witness) {
		this.witness = witness;
	}

	public boolean isWitnessed() {
		return witnessed;
	}

	@Transient
	public boolean isDeclined() {
		return !isWitnessed() && getWitnessesDate() != null;
	}

	public void setWitnessed(boolean witnessed) {
		this.witnessed = witnessed;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	public String getOptionString() {
		return optionString;
	}

	public void setOptionString(String option) {
		this.optionString = option;
	}

	@OneToOne
	@JsonIgnore
	public User getWitness() {
		return witness;
	}

	public void setWitness(User witness) {
		this.witness = witness;
	}

	@Temporal(TemporalType.TIMESTAMP)
	public Date getWitnessesDate() {
		return witnessesDate == null ? null : new Date(witnessesDate.getTime());
	}

	public void setWitnessesDate(Date witnessesDate) {
		this.witnessesDate = witnessesDate == null ? null : new Date(witnessesDate.getTime());
	}

	@ManyToOne()
	@JsonIgnore
	public Signature getSignature() {
		return signature;
	}

	public void setSignature(Signature signature) {
		this.signature = signature;
	}

}