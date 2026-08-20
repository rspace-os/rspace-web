package com.researchspace.model.preference;

/**
 * Permission type that supports trinary permissions: denied, denied by default, allowed.
 */
public enum HierarchicalPermission {

    DENIED_BY_DEFAULT,

    ALLOWED,

    DENIED;

    public static final HierarchicalPermission DEFAULT_SYS_ADMIN_PERMISSION = DENIED_BY_DEFAULT;
    public static final HierarchicalPermission DEFAULT_COMMUNITY_PERMISSION = DENIED_BY_DEFAULT;

    /**
     * Converts true / false permissions to Permission Enum values. True is converted to ALLOWED. False is converted
     * to DENIED. Permission Enum values are not changed.
     * @param value
     * @return Permission Enum value string or UNKNOWN
     */
    public static String toPermissionEnumString(String value) {
        switch (value.toLowerCase()) {
            case "true":
                return HierarchicalPermission.ALLOWED.toString();
            case "false":
                return HierarchicalPermission.DENIED.toString();
        }
        try {
            return HierarchicalPermission.valueOf(value).toString();
        } catch (IllegalArgumentException e) {
            return "UNKNOWN";
        }
    }
}
