package com.researchspace.model;

/**
 * Defines signature status for display in UI.
 */
public enum SignatureStatus {
    UNSIGNED,
    SIGNED_AND_LOCKED,
    /**
     * The document has been signed and locked, but it is still not witnessed
     */
    AWAITING_WITNESS,
    /**
     * The document has been signed, locked and witnessed.
     */
    WITNESSED,
    /**
     * The document cannot be signed for any reason.
     */
    UNSIGNABLE,
    /**
     * The document has been signed, locked and witnesses were added, however, all witnesses declined.
     */
    SIGNED_AND_LOCKED_WITNESSES_DECLINED
}
