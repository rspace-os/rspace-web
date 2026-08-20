package com.researchspace.model.record;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.permissions.RecordSharingAclTestMother;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class InheritACLFromParentsPropagationPolicyTest {
    private InheritACLFromParentsPropagationPolicy inheritACLFromParentsPropagationPolicy;
    @Mock
    private BaseRecord parent;
    @Mock
    private BaseRecord child;
    @Captor
    private ArgumentCaptor<List<ACLElement>> aclCaptor;
    private RecordSharingACL parentACLWithoutAnonymous = RecordSharingAclTestMother.
            createRecordSharingAclAllSamePermission(new String[]{"el1", "el2", "el3"},"RECORD:CREATE:");
    private RecordSharingACL parentACLWithAnonymous = RecordSharingAclTestMother.
            createRecordSharingAclAllSamePermission(new String[]{"el1", "el2", "el3", RecordGroupSharing.ANONYMOUS_USER},"RECORD:CREATE:");
    private RecordSharingACL childACLWithAnonymous = RecordSharingAclTestMother.
            createRecordSharingAclAllSamePermission(new String[]{"el1", "el2", "el3", RecordGroupSharing.ANONYMOUS_USER},"RECORD:CREATE:");

    @BeforeEach
    public void setUp(){
        openMocks(this);
        when(parent.getSharingACL()).thenReturn(parentACLWithoutAnonymous);
        when(child.getSharingACL()).thenReturn(RecordSharingAclTestMother.
                createRecordSharingAclAllSamePermission(new String[]{"el1", "el2", "el3", "el4"},"RECORD:CREATE:"));
        inheritACLFromParentsPropagationPolicy = new InheritACLFromParentsPropagationPolicy();

    }

    @Test
    public void shouldRemovePermissionsInCommonWithParent(){
        when(child.removeACLs(aclCaptor.capture())).thenReturn(child);
        inheritACLFromParentsPropagationPolicy.onRemove(parent,child);
        List<ACLElement> removedFromChild = aclCaptor.getValue();
        assertTrue(removedFromChild.equals(parentACLWithoutAnonymous.getAclElements()));
    }
    @Test
    public void shouldNotRemoveAnonymousUserPermissionsInCommonWithParent(){
        when(parent.getSharingACL()).thenReturn(parentACLWithAnonymous);
        when(child.getSharingACL()).thenReturn(childACLWithAnonymous);
        when(child.removeACLs(aclCaptor.capture())).thenReturn(child);
        inheritACLFromParentsPropagationPolicy.onRemove(parent,child);
        List<ACLElement> removedFromChild = aclCaptor.getValue();
        assertTrue(removedFromChild.equals(parentACLWithoutAnonymous.getAclElements()));
    }
}
