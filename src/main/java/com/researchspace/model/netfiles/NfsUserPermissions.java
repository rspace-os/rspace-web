package com.researchspace.model.netfiles;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Per-user, per-filesystem permissions snapshot exposed on listing responses. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NfsUserPermissions implements Serializable {

	private static final long serialVersionUID = 1L;

	private boolean canRead;
	private boolean canWrite;
}
