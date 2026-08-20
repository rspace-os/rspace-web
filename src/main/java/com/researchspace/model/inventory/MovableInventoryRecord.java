package com.researchspace.model.inventory;

import java.time.Instant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;

@MappedSuperclass
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@Audited
public abstract class MovableInventoryRecord extends InventoryRecord {

	private ContainerLocation parentLocation;

	private Container lastNonWorkbenchParent;

	private Instant lastMoveDate;

	/**
	 * @return location inside parent container, or null if record is not stored anywhere
	 */
	@OneToOne(cascade = CascadeType.MERGE)
	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@NotFound(action = NotFoundAction.IGNORE)
	public ContainerLocation getParentLocation() {
		return parentLocation;
	}

	/**
	 * @return parent container, or null if record is not stored anywhere
	 */
	@Transient
	public Container getParentContainer() {
		if (getParentLocation() == null) {
			return null;
		}
		return getParentLocation().getContainer();
	}

	/**
	 * @return true if the MovableInventoryRecord is currently
	 * located into (linked to) a proper Container (so not in the workbench)
	 */
	@Transient
	public boolean isStoredInContainer() {
		return this.getParentContainer() != null && !this.getParentContainer().isWorkbench();
	}
	/**
	 * @return id of parent container, or null if record is not stored anywhere
	 */
	@Transient
	@GenericField
	@IndexingDependency(derivedFrom = @ObjectPath(@PropertyValue(propertyName = "parentLocation")))
	public Long getParentId() {
		if (getParentContainer() != null) {
			return getParentContainer().getId();
		}
		return null;
	}
	
	public void moveToNewParent(Container targetParent) {
		removeFromCurrentParent();
		targetParent.addToNewLocation(this);
	}

	public void moveToNewParentWithCoords(Container targetParent, Integer coordX, Integer coordY) {
		removeFromCurrentParent();
		targetParent.addToNewLocationWithCoords(this, coordX, coordY);
	}
	
	public void moveToNewParentAndLocation(Container targetParent, ContainerLocation targetLocation) {
		removeFromCurrentParent();
		targetParent.setRecordInLocation(this, targetLocation);
	}

	public void removeFromCurrentParent() {
		Container currentParent = getParentContainer();
		if (currentParent != null) {
			if (!currentParent.isWorkbench()) {
				setLastNonWorkbenchParent(currentParent);
			}
			currentParent.removeStoredRecord(getParentLocation());
			setParentLocation(null);
		}
	}

	@Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
	@OneToOne(cascade = CascadeType.MERGE)
	public Container getLastNonWorkbenchParent() {
		return lastNonWorkbenchParent;
	}

	public void setLastNonWorkbenchParent(Container lastNonWorkbenchParent) {
		if (lastNonWorkbenchParent != null && lastNonWorkbenchParent.isWorkbench()) {
			throw new IllegalArgumentException("Can't set workbench as lastNonWorkbenchParent");
		}
		this.lastNonWorkbenchParent = lastNonWorkbenchParent;
	}

}
