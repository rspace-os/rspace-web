package com.researchspace.service.dmp;

import java.net.URL;

/**
 * What a deposit supplies for write-back to a plan. Either value may be null; {@code accessUrl} is
 * set only when {@code identifier} is null.
 *
 * @param repositoryApp display name of the repository deposited to
 * @param identifier the DOI link
 * @param accessUrl the repository link, used when there is no DOI link
 */
public record DepositReport(String repositoryApp, URL identifier, URL accessUrl) {}
