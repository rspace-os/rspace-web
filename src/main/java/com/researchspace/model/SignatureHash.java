package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Entity
@org.hibernate.annotations.BatchSize(size = 4) //there are 4 hashes per signature, we can load these in 1 query.
public class SignatureHash implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1342261215693448724L;
	
	private Long id;
    private String hexValue;
    private SignatureHashType type;
    private FileProperty file;
    private Signature signature;
    private static final int HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    @Column(nullable = false, length = HASH_LENGTH)
    @Size(min = HASH_LENGTH, max = HASH_LENGTH)
    @Pattern(regexp="[A-Fa-f0-9]+")
    public String getHexValue() {
        return hexValue;
    }

    void setHexValue(String hexValue) {
        this.hexValue = hexValue;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SignatureHashType getType() {
        return type;
    }

     void setType(SignatureHashType type) {
        this.type = type;
    }

    @OneToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    public FileProperty getFile() {
        return file;
    }

    void setFile(FileProperty file) {
        this.file = file;
    }

    @ManyToOne
    @JoinColumn(name = "signature_id", nullable = false)
    public Signature getSignature() {
        return signature;
    }

     void setSignature(Signature signature) {
        this.signature = signature;
    }
    
    public SignatureHashInfo toSignatureHashInfo() {
        SignatureHashInfo info = new SignatureHashInfo();
        info.setId(id);
        info.setType(type.name());
        info.setHexValue(hexValue);
        if (file != null) {
            info.setFilePropertyId(file.getId());
        }
        return info;
    }
    
}
