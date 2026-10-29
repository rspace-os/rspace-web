package com.researchspace.service;

/** Whether forms and templates of the user are used by the other users */
public record FormsAndTemplatesUsage(
    boolean formsUsedByOtherUsers, boolean templatesUsedByOtherUsers) {}
