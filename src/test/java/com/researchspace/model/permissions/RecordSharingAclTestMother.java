package com.researchspace.model.permissions;

import java.util.ArrayList;
import java.util.List;

public class RecordSharingAclTestMother {
    private static ConstraintPermissionResolver permResolver;

    public static RecordSharingACL createRecordSharingAclAllSamePermission(String[] groupNames, String permission) {
        permResolver = new ConstraintPermissionResolver();
        List<ACLElement> acls = new ArrayList<>();
        for (String gName : groupNames) {
            acls.add(new ACLElement(gName, permResolver.resolvePermission(permission)));
        }
        RecordSharingACL recordSharingACL = new RecordSharingACL();
        for (ACLElement acl : acls) {
            recordSharingACL.addACLElement(acl);
        }
        return recordSharingACL;
    }
}
