package com.researchspace.model.record;

import static org.apache.commons.lang3.StringUtils.abbreviate;

import java.time.Instant;

import org.apache.commons.lang3.Validate;

import lombok.Value;
/**
 * Data to override default properties when creating new Records from XML imports
 */
@Value
public class ImportOverride {
	private Instant created, lastModified;
	private String originalCreatorUsername;
	
	static final int TOLERANCE_SECONDS = 10;
	
	/**
	 * @param created
	 * @param lastModified
	 * @param originalCreatorUsername, nullable
	 */
	public ImportOverride(Instant created, Instant lastModified, String originalCreatorUsername) {
		this(created, lastModified, originalCreatorUsername, false);
	}

	/**
	 * @param created
	 * @param lastModified
	 * @param originalCreatorUsername, nullable
	 * @param allowCreationDateAfterModificationDate whether creation date that is later than modification date should be allowed
	 */
	public ImportOverride(Instant created, Instant lastModified, String originalCreatorUsername, boolean allowCreationDateAfterModificationDate) {
		Validate.noNullElements(new Object[] { created, lastModified }, "Creation/modification dates must be non-null");

		/* allow some tolerance, it seems that creation date can be some millis after modified date */
		if (created.minusSeconds(TOLERANCE_SECONDS).isAfter(lastModified)) {
			/* rspac-2759, allow skipping the check if server has known issue with dates */
			if (!allowCreationDateAfterModificationDate) {
				throw new IllegalArgumentException("Last modified date: " + lastModified
						+ " must not be before creation date: " + created);
			}
		}
		this.created = created;
		this.lastModified = lastModified;
		this.originalCreatorUsername = abbreviate(originalCreatorUsername, 50);
	}

}
