package com.researchspace.api.v2.model;

import java.util.Map;

/**
 * The first page of several runtime-field catalogs of one collection, searched with one term.
 *
 * @param catalogs each requested namespace's catalog page, in request order
 */
public record ApiV2RuntimeFieldSearch(Map<String, ApiV2RuntimeFieldCatalog> catalogs) {}
