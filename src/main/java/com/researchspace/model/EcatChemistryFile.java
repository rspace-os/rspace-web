package com.researchspace.model;

import jakarta.persistence.*;

import com.researchspace.model.record.DOCUMENT_CATEGORIES;
import com.researchspace.model.record.ImportOverride;

import com.researchspace.model.record.RecordInformation;
import lombok.*;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import java.io.Serializable;

@Entity
@Audited
@Indexed
@NoArgsConstructor
public class EcatChemistryFile extends EcatMediaFile implements Serializable {
	
	public EcatChemistryFile(ImportOverride override) {
		super(override);
	}

    private static final long serialVersionUID = 6063045468477346488L;

    /**
     * String representation of the chemistry file
     */
    private String chemString;

    @Lob
    public String getChemString() {
        return chemString;
    }

    public void setChemString(String chemString) {
        this.chemString = chemString;
    }

    @Transient
    @Override
    public String getRecordInfoType() {
        return DOCUMENT_CATEGORIES.ECATCHEMISTRY;
    }

    @Override
    @Transient
    public boolean isChemistryFile() {
        return true;
    }


    public RecordInformation toRecordInfo() {
        RecordInformation recordInformation = super.toRecordInfo();
        recordInformation.setChemString(this.chemString);
        return recordInformation;
    }

    @Override
    public EcatChemistryFile copy() {
        EcatChemistryFile copy = new EcatChemistryFile();
        copy.setChemString(getChemString());
        this.shallowCopyEcatMedia(copy);
        return copy;
    }
}
