package com.researchspace.core.util.version;

import com.researchspace.core.util.IDescribable;

/**
 * RSpace services that are versionable.
 */
public interface Versionable extends IDescribable {

	SemanticVersion getVersion();

	String getVersionMessage();

}
