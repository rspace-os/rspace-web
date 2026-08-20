package com.researchspace.model;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.apache.shiro.crypto.hash.Hash;

import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;

/**
 * POJO class holding information about a record signing event.
 */
@Entity
public class Signature implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Long id;
	private String statement;
	private User signer;
	private Record recordSigned;
	private Date signatureDate;
	
	private Set<Witness> witnesses = new HashSet<>();
	private MessageOrRequest witnessRequest;
    private Set<SignatureHash> hashes = new HashSet<>();

    @Override
	public String toString() {
		return "Signature [id=" + id + ", statement=" + statement + ", signer=" + signer + ", recordSigned="
				+ recordSigned + ", signatureDate=" + signatureDate + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((signatureDate == null) ? 0 : signatureDate.hashCode());
		result = prime * result + ((signer == null) ? 0 : signer.hashCode());
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
		Signature other = (Signature) obj;
		if (signatureDate == null) {
			if (other.signatureDate != null) {
				return false;
			}
		} else if (!signatureDate.equals(other.signatureDate)) {
			return false;
		}
		if (signer == null) {
			if (other.signer != null) {
				return false;
			}
		} else if (!signer.equals(other.signer)) {
			return false;
		}
		return true;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	public Long getId() {
		return id;
	}

	void setId(Long id) {
		this.id = id;
	}

	public String getStatement() {
		return statement;
	}

	public void setStatement(String statement) {
		this.statement = statement;
	}

	@ManyToOne(optional = false)
	public User getSigner() {
		return signer;
	}

	public void setSigner(User signer) {
		this.signer = signer;
	}

	// we don't access this association anywhere outside session
	@OneToOne(optional = false, fetch = FetchType.LAZY) 
	public Record getRecordSigned() {
		return recordSigned;
	}

	public void setRecordSigned(Record recordSigned) {
		this.recordSigned = recordSigned;
	}

	@Temporal(TemporalType.TIMESTAMP)
	public Date getSignatureDate() {
		return signatureDate == null ? null : new Date(signatureDate.getTime());
	}

	public void setSignatureDate(Date signatureDate) {
		this.signatureDate = signatureDate == null ? null : new Date(signatureDate.getTime());
	}

	@OneToMany(mappedBy = "signature", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	public Set<Witness> getWitnesses() {
		return witnesses;
	}

	/**
	 * A reference to the request that was sent to witnesses.
	 * 
	 * @return
	 */
	@OneToOne
	public MessageOrRequest getWitnessRequest() {
		return witnessRequest;
	}

	public void setWitnessRequest(MessageOrRequest witnessRequest) {
		this.witnessRequest = witnessRequest;
	}

	public void addWitnesses(List<Witness> witnesses) {
		for (Witness toAdd : witnesses) {
			this.witnesses.add(toAdd);
			toAdd.setSignature(this);
		}
	}

	public SignatureInfo toSignatureInfo() {
		SignatureInfo info = new SignatureInfo();
		info.setId(id);
		if (signer != null) {
			info.setSignerFullName(signer.getFullName());
			info.setSignDate(signatureDate.toString());
			if (witnesses.isEmpty()) {
				info.setStatus(SignatureStatus.SIGNED_AND_LOCKED);
			} else {
				info.setStatus(SignatureStatus.AWAITING_WITNESS);

				boolean allWitnessesDeclined = true;
				for (Witness w : witnesses) {
					String witnessingDate = w.isWitnessed() ? w.getWitnessesDate().toString() : null;

					if (w.isDeclined())
						info.getWitnesses().put(w.getWitness().getFullName(), "DECLINED");
					else
						info.getWitnesses().put(w.getWitness().getFullName(), witnessingDate);

					if (w.isWitnessed()) {
						info.setStatus(SignatureStatus.WITNESSED);
						allWitnessesDeclined = false;
					}
					if (w.getWitnessesDate() == null) {
						// This witness hasn't responded yet
						allWitnessesDeclined = false;
					}
				}
				if (allWitnessesDeclined) {
					info.setStatus(SignatureStatus.SIGNED_AND_LOCKED_WITNESSES_DECLINED);
				}

			}
			for (SignatureHash hash : hashes) {
				info.getHashes().add(hash.toSignatureHashInfo());
			}
		} else {
			info.setStatus(SignatureStatus.UNSIGNED);
		}
		return info;
	}

	/*
	 * For hibernate
	 */
	void setWitnesses(Set<Witness> witnesses) {
		this.witnesses = witnesses;
	}

    @OneToMany(cascade = CascadeType.ALL)
    @JoinColumn(name = "signature_id")
    public Set<SignatureHash> getHashes() {
        return hashes;
    }

    public void setHashes(Set<SignatureHash> hashes) {
        this.hashes = hashes;
    }

    public void generateRecordContentHash() {
        if (!recordSigned.isStructuredDocument()) {
            throw new UnsupportedOperationException("only structured documents can be signed");
        }
        
        StructuredDocument doc = (StructuredDocument) recordSigned;
        Hash hash = doc.getRecordContentHashForSigning();
        addHash(hash, SignatureHashType.CONTENT, null);
    }

    public void addHash(Hash hash, SignatureHashType hashType, FileProperty fileProperty) {
        SignatureHash singatureHash = new SignatureHash();
        singatureHash.setHexValue(hash.toHex());
        singatureHash.setType(hashType);
        singatureHash.setFile(fileProperty);
        singatureHash.setSignature(this);
        hashes.add(singatureHash);
    }

}