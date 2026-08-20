package com.researchspace.model.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.core.UniquelyIdentifiable;
import org.hibernate.envers.Audited;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Basic model used to represent all Files and document types within eCAT.
 */
@Entity
@Audited
@Inheritance(strategy = InheritanceType.JOINED)
// @Searchable(poly = true)
@XmlRootElement
public abstract class Record extends BaseRecord implements Serializable, UniquelyIdentifiable {

    private static final long serialVersionUID = 6988069642163178409L;

    private Set<RecordAttachment> linkedMediaFiles = new HashSet<>();
    private Record tempRecord;

    public Record() {
    }


    public Record(ImportOverride override) {
        super(override);
    }

    /**
     * Bidirectional association between media files and records. This is the
     * owning side of the relationship.
     *
     * @return return possibly empty but non-null set of linked media files.
     */
    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL)
    @JsonIgnore
    public Set<RecordAttachment> getLinkedMediaFiles() {
        return linkedMediaFiles;
    }

    /*
     * For hibernate only. Client should use addMediaFileLinks
     */
    void setLinkedMediaFiles(Set<RecordAttachment> linkedMediaFiles) {
        this.linkedMediaFiles = linkedMediaFiles;
    }

    /**
     * Creates an association between this field and an {@link EcatMediaFile}
     *
     * @param mediaFile
     * @return <code>true</code> if mediaFile is successfully added to this
     * field
     */
    public boolean addMediaFileLink(EcatMediaFile mediaFile) {
        RecordAttachment link = new RecordAttachment(this, mediaFile);
        boolean fieldAdded = mediaFile.getLinkedRecords().add(link);
        boolean mediaAdded = linkedMediaFiles.add(link);
        return fieldAdded && mediaAdded;
    }

    /**
     * Always returns empty list since Records don't have children; this method
     * is present just so that getChildren can be called recursively while
     * descending a folder tree without testing for the type of BaseRecord.
     */
    @Transient
    public final Set<BaseRecord> getChildrens() {
        return Collections.<BaseRecord>emptySet();
    }

    @Transient
    public boolean isEditable() {
        return true;
    }

    /**
     * Boolean test for whether a record is a Template document or not
     *
     * @return
     */
    @Transient
    public boolean isTemplate() {
        return hasType(RecordType.TEMPLATE) && isStructuredDocument();
    }

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    public Record getTempRecord() {
        return tempRecord;
    }

    public void setTempRecord(Record tempRecord) {
        this.tempRecord = tempRecord;
    }

    /**
     * Boolean test for whether a record has been deleted from a user's folder.
     *
     * @param u A non-null user
     * @return <code>true</code>if has been deleted from user's folder, false
     * otherwise.
     */
    public boolean isDeletedForUser(User u) {
        for (RecordToFolder r2f : getParents()) {
            if (r2f.isRecordInFolderDeleted() && r2f.getUserName().equals(u.getUsername())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Must be overridden by subclasses!!
     *
     * @return
     */
    public Record copy() {
        return null;
    }

    @Override
    public String toString() {
        return "Record [id=" + getId() + ", editInfo=" + getEditInfo() + ", type=" + getType() + "]";
    }

    /**
     * Boolean test for whether this record is an image
     *
     * @return
     */
    @Transient
    public boolean isImage() {
        return false;
    }

    @Transient
    public GlobalIdentifier getOidWithVersion() {
        return getOid(); // subclasses can override with their versioning
    }

}
