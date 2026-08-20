package com.researchspace.zipprocessing;

import java.io.File;

/**
 * Processes a single Zip entry that is available as a {@link File}.
 */
@FunctionalInterface
public interface ZipEntryFileProcessor {

	void process(File entryAsFile);

}
