package com.researchspace.model;

import static com.researchspace.model.permissions.ConstraintBasedPermissionTest.createPermissonActionSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.authz.Permission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.ConstraintBasedPermissionTest;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.permissions.EntityPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.PropertyConstraint;
import com.researchspace.model.preference.Preference;

@ExtendWith(MockitoExtension.class)
public class UserTest {
	
	private User u1, u2, u3;
	private Group gp1;
	private Group gp2;


	@BeforeEach
	public void setUp() throws Exception {
		u1 = new User("u1");
		u2 = new User("u2");
		u3 = new User("u3");
		gp1 = new Group("g1");
		gp2 = new Group("g2");
	}
	@AfterEach
	public void tearDown() throws Exception {
	}
	
	@Test
	public void testCreationDateSetOnObjectCreation() {
		assertNotNull(u1.getCreationDate());
		assertNotNull(gp1.getCreationDate());
		// by default is a permanent account.
		assertFalse(u1.isTempAccount());
	}

	@Test
	public void testNaturalORderinf() {
		List<User> unorderedusers = Arrays.asList(new User[] { u2, u3, u1 });
		Collections.sort(unorderedusers);
		assertEquals(u1, unorderedusers.get(0));
		assertEquals(u3, unorderedusers.get(2));
	}
	
	@Test
	public void testLastNameComparator() {
		u1.setLastName("adams");
		u2.setLastName("michael");
		u3.setLastName("zlob");
		List<User> unorderedusers = Arrays.asList(new User[] { u2, u3, u1 });
		Collections.sort(unorderedusers, User.LAST_NAME_COMPARATOR);
		assertEquals(u1, unorderedusers.get(0));
		assertEquals(u3, unorderedusers.get(2));
	}
	
	@Test
	public void testContentInitialisedIsFalseInNewUser() {
		assertFalse(u1.isContentInitialized());
	}

	@Test
	public void testIsPermitted() {
		ConstraintBasedPermission p1 = createAndConfigureAPermission();
		
		u1.addPermission(p1);
		//matches whether or not inherit is true, since does not belong to group
		assertTrue(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
		assertTrue(u1.isPermitted(getMatchingEntityPermissionFromRecord(), true));
		
		u1.removePermission(p1);
		assertFalse(u1.isPermitted(getMatchingEntityPermissionFromRecord(), false));
	}

	public ConstraintBasedPermission createAndConfigureAPermission() {
		// create READ Only permission for docs  created in a range owned by 'user'
		ConstraintBasedPermission p1 = new ConstraintBasedPermission(PermissionDomain.RECORD, 
				createPermissonActionSet(PermissionType.READ));
		PropertyConstraint pc = ConstraintBasedPermissionTest.createAPropertyConstraint("ownedBy", "user");
		p1.addPropertyConstraint(pc);
		return p1;
	}
	
	@Test
	public void testGroupShare() {
		gp1.addMember(u1);
		gp1.addMember(u2);
		gp2.addMember(u3);
		// check is transitive
		assertTrue(u1.isInSameGroupAs(u2));
		assertTrue(u2.isInSameGroupAs(u1));
		//sanity check
		assertTrue(u1.isInSameGroupAs(u1));
		
		assertFalse(u1.isInSameGroupAs(u3));
		assertFalse(u3.isInSameGroupAs(u1));
		
		// remove a member, should now be false
		gp1.removeMember(u1);
		assertFalse(u1.isInSameGroupAs(u2));
	}

	@Test
	public void testPreferencesReturnsDefaultValue() {
		assertEquals(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF.getDefaultValue(),
				u1.getValueForPreference(Preference.NOTIFICATION_DOCUMENT_EDITED_PREF).getValue());
	}

	@Test
	public void testDefaultWantNotificationOptions() {
		assertEquals(true, u1.wantsNotificationFor(NotificationType.PROCESS_COMPLETED));
		assertEquals(true, u1.wantsNotificationFor(NotificationType.ARCHIVE_EXPORT_COMPLETED));
		
		//ignore background pref when export completed, RSPAC-1970
		u1.setPreference(new UserPreference(Preference.PROCESS_COMPLETED_PREF, u1, "false"));
		assertEquals(true, u1.wantsNotificationFor(NotificationType.ARCHIVE_EXPORT_COMPLETED));	
	}
	
	@Test
	public void testGetCollabGroups() {
		gp1.addMember(u1);
		assertEquals(0, u1.getCollaborationGroups().size());
		gp1.setGroupType(GroupType.COLLABORATION_GROUP);
		assertEquals(1, u1.getCollaborationGroups().size());
		
	}
	
	@Test
	public void testUserEditPermission() {
		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
		ConstraintBasedPermission cbp = parser.resolvePermission("USER:WRITE:property_username=${self}");
		u1.addPermission(cbp);
		u2.addPermission(cbp);

		ConstraintBasedPermission toTestU1 = parser.resolvePermission("USER:WRITE:property_username=" + u1.getUsername());
		ConstraintBasedPermission toTestU2 = parser.resolvePermission("USER:WRITE:property_username=" + u2.getUsername());

		assertTrue(u1.isPermitted(toTestU1, false));
		assertFalse(u2.isPermitted(toTestU1, false));
		assertFalse(u1.isPermitted(toTestU2, false));
		assertTrue(u2.isPermitted(toTestU2, false));
	}
	
	@Test
	public void testIsPermittedInheritance() {
        ConstraintBasedPermission p1 = createAndConfigureAPermission();
		//permission added to inherited group
		gp1.addPermission(p1);
		gp1.addMember(u1);
		
		//matches only if  inherit is true
		Permission recordInfo = getMatchingEntityPermissionFromRecord();
		assertFalse(u1.isPermitted(recordInfo, false));
		assertTrue(u1.isPermitted(recordInfo, true));
		// now disable permission
		p1.setEnabled(false);
		assertFalse(u1.isPermitted(recordInfo, true));
		p1.setEnabled(true);
		assertTrue(u1.isPermitted(recordInfo, true));
		// now remove permission altogether
		gp1.removePermission(p1);
		assertFalse(u1.isPermitted(recordInfo, true));
		// now add back
		gp1.addPermission(p1);
		assertEquals(1, u1.getAllPermissions(false, true).size());
		
		// now we'll unset permissions from gp1
		u1.setIncludePermissionForGroup(gp1, false);
		assertFalse(u1.isPermitted(recordInfo, true));
		assertEquals(0, u1.getAllPermissions(false, true).size());
		
		// and reset permissions from gp1, permission restored
	    u1.setIncludePermissionForGroup(gp1, true);
		assertTrue(u1.isPermitted(recordInfo, true));
	
	}

	private EntityPermission getMatchingEntityPermissionFromRecord() {
		EntityPermission ep = new EntityPermission(PermissionDomain.RECORD,
				PermissionType.READ);
		ep.addPropertyConstraint(ConstraintBasedPermissionTest.createAPropertyConstraint("ownedBy", "user"));
		return ep;
	}
	
	@Test
	public void testParseMultiuser() {
		String userStr1 = " user1<Bob Jones>,user2 <Simon Smith>,user3<Simn mith>";
		String userStr4 = " user1<Bob Jones>,user2<Simon Smith>,user3<Simn mith>";
		String userStr2 = "user1<Bob Jones>,user2,user3";
		String userStr3 = " user1 , user2 , user3 ";
		
		String[] tests = new String[] { userStr1, userStr2, userStr3, userStr4 };
		for (String test : tests) {
			String[] parsed = User.getUsernamesFromMultiUser(test);
			assertEquals( 3, parsed.length,"parsing [" + test + "] failed");
			assertEquals("user1", parsed[0]);
			assertEquals("user2", parsed[1]);
			assertEquals("user3", parsed[2]);
		}
		String singleUser = " user1<Bob Jones>, "; // ignore blank suffix
		String [] parsed = User.getUsernamesFromMultiUser(singleUser);
		assertEquals(1, parsed.length);
	}

	@Test
	public void testUserEditableAttributesAreTruncatedIfTooLong() {
		String TOO_LONG_text = CoreTestUtils.getRandomName(User.MAX_UNAME_LENGTH + 1);
		u1.setFirstName(TOO_LONG_text);
		assertEquals(User.MAX_UNAME_LENGTH, u1.getFirstName().length());

		u1.setLastName(TOO_LONG_text);
		assertEquals(User.MAX_UNAME_LENGTH, u1.getLastName().length());

	}
	
	@Test
	public void testEmailThrowsIAEIfTooLong() {
		String TOO_LONG_text = CoreTestUtils.getRandomName(User.DEFAULT_MAXFIELD_LEN + 1);
		assertThrows(IllegalArgumentException.class, ()->u1.setEmail(TOO_LONG_text));
	}
	
	@Test
	public void addRemoveRole() {
		Role userrole = Role.USER_ROLE;
		u1.addRole(userrole);
		assertEquals(1, u1.getRoles().size());
		
		// not removed as would leave no roles, if removed
		assertFalse(u1.removeRole(userrole));
		assertEquals(1, u1.getRoles().size());
		
		// not removed, as does not have role
		assertFalse(u1.removeRole(Role.PI_ROLE));
		Role pirole = Role.PI_ROLE;
		u1.addRole(pirole);
		assertEquals(2, u1.getRoles().size());
		
		// now can remove PI role as still has user role
		assertTrue(u1.removeRole(pirole));
		assertEquals(1, u1.getRoles().size());
	}
	
	@Test
	public void testReplaceRole() {
		Role userrole = Role.USER_ROLE;
		u1.addRole(userrole);
		assertTrue(u1.hasRole(userrole));
		// has role returns true if any roles match
		assertTrue(u1.hasRole(userrole, Role.ADMIN_ROLE));
	}
	
	@Test
	public void testGetPrimaryLabGroup (){
		u1.addRole( Role.USER_ROLE);
		// wrong role
		assertNull(u1.getPrimaryLabGroupWithPIRole());
		assertFalse(u1.isPIOfLabGroup());
		assertFalse(u1.isPiOrLabAdminOfGroup(gp1));
		u1.addRole(Role.PI_ROLE);
		// no group
		assertNull(u1.getPrimaryLabGroupWithPIRole());
		assertFalse(u1.isPIOfLabGroup());
		
		gp1.setGroupType(GroupType.COLLABORATION_GROUP);
		gp1.addMember(u1,RoleInGroup.DEFAULT);
		// wrong group type
		assertNull(u1.getPrimaryLabGroupWithPIRole());
		assertFalse(u1.isPIOfLabGroup());
		assertFalse(u1.isPiOrLabAdminOfGroup(gp1));
		
		gp1.setGroupType(GroupType.LAB_GROUP);
		// not PI role
		assertNull(u1.getPrimaryLabGroupWithPIRole());
		assertFalse(u1.isPIOfLabGroup());
		assertFalse(u1.isPiOrLabAdminOfGroup(gp1));
		u1.getUserGroups().iterator().next().setRoleInGroup(RoleInGroup.PI);
		assertEquals(gp1,u1.getPrimaryLabGroupWithPIRole());
		assertTrue(u1.isPIOfLabGroup());
		assertTrue(u1.isPiOrLabAdminOfGroup(gp1));
	}
	
	@Test
	public void affiliationTestThrowsIAEIfEmpty() {
		assertThrows(IllegalArgumentException.class, ()->u1.setAffiliation("", ProductType.COMMUNITY));
	}
	
	@Test()
	public void affiliationCanBeEmptyForNoncloudVersions() {
		u1.setAffiliation("", ProductType.STANDALONE);
	}
	
	@Test
	public void testGetRolesNames() {
		assertEquals("", u1.getRolesNamesAsString());
		u1.addRole(Role.PI_ROLE);
		assertEquals(Role.PI_ROLE.getName(), u1.getRolesNamesAsString());
		u1.addRole(Role.ADMIN_ROLE);
		assertTrue(u1.getRolesNamesAsString().startsWith(Role.ADMIN_ROLE.getName()) 
				|| u1.getRolesNamesAsString().endsWith(Role.ADMIN_ROLE.getName()));
	}
	
	@Test
	public void hasrole() {
		Set<Role> roles = new HashSet<>();
		Role pirole = Role.PI_ROLE;
		pirole.setDescription("PI role desc");
		roles.add(pirole);
		u1.setRoles(roles);
		assertTrue(u1.isPI());
	}
	@Test
	public void hasRoleInGroup() {
		assertFalse(u1.isPI());
		u1.addRole(Role.PI_ROLE);
		assertTrue(u1.isPI());
		
		gp1.addMember(u1, RoleInGroup.PI);
		gp1.addMember(u2);
		assertTrue(u1.hasRoleInGroup(gp1, RoleInGroup.PI));
		assertFalse(u2.hasRoleInGroup(gp1, RoleInGroup.PI));
	}
	
	@Test
	public void hasRolePiOrLabGroupViewAllInGroup() {
		u1.addRole(Role.PI_ROLE);
		gp1.addMember(u1, RoleInGroup.PI);
		gp1.addMember(u2, RoleInGroup.RS_LAB_ADMIN);
		gp1.addMember(u3);
		gp1.getUserGroupForUser(u2).setAdminViewDocsEnabled(true);
		assertTrue(u1.hasAnyPiOrLabGroupViewAllRole());
		assertTrue(u2.hasAnyPiOrLabGroupViewAllRole());
		assertFalse(u3.hasAnyPiOrLabGroupViewAllRole());
	}
	
	@Test
	public void testGetAllGroupMembers() {
		u1.addRole(Role.PI_ROLE);
		gp1.addMember(u1, RoleInGroup.PI);
		gp1.addMember(u2);
		gp2.addMember(u3);
		gp2.addMember(u1);
		
		assertEquals(3, u1.getAllGroupMembers().size());
		//u1 is only pi in gp1. 
		assertEquals(1, u1.getNonPiLabGroupMembersForPiOrViewAllAdmin().size());
		assertEquals(0, u2.getNonPiLabGroupMembersForPiOrViewAllAdmin().size());
		gp1.getUserGroupForUser(u2).setRoleInGroup(RoleInGroup.RS_LAB_ADMIN);
		gp1.setLabAdminViewAll(u2, false);
		// can't get group members
		assertEquals(0, u2.getNonPiLabGroupMembersForPiOrViewAllAdmin().size());
		gp1.setLabAdminViewAll(u2, true);
		// now is like PI
		assertEquals(1, u2.getNonPiLabGroupMembersForPiOrViewAllAdmin().size());
	}
	
	@Test
	public void autoshareGroupSetup() {
		u1.addRole(Role.PI_ROLE);
		gp1.addMember(u1, RoleInGroup.PI);
		gp1.addMember(u2);
		// no autoshare groups set up
		assertFalse(u2.hasAutoshareGroups());
		assertEquals(0, u2.getAutoshareGroups().size());
		
		u2.getUserGroups().iterator().next().setAutoshareEnabled(true);
		// after setting up:
		assertTrue(u2.hasAutoshareGroups());
		assertEquals(1, u2.getAutoshareGroups().size());
	}

	@Test
	public void groupMembersWithViewAll() {
		User user = new User("u1");

		Group gp1 = new Group("g1");
		User gp1Pi1 = new User("u2");
		gp1Pi1.addRole(Role.PI_ROLE);
		User gp1Pi2 = new User("u3");
		gp1Pi2.addRole(Role.PI_ROLE);
		User gp1LabAdmin = new User("u4");
		User gp1LabAdminViewAll = new User("u5");
		User gp1RegularUser = new User("u6");
		gp1.addMember(gp1Pi1, RoleInGroup.PI);
		gp1.addMember(gp1Pi2, RoleInGroup.PI);
		gp1.addMember(gp1LabAdmin, RoleInGroup.RS_LAB_ADMIN);
		gp1.addMember(gp1LabAdminViewAll, RoleInGroup.RS_LAB_ADMIN);
		gp1.addMember(gp1RegularUser, RoleInGroup.DEFAULT);
		enableViewAll(gp1, gp1LabAdminViewAll);

		Group gp2 = new Group("g2");
		User gp2Pi = new User("u6");
		gp2Pi.addRole(Role.PI_ROLE);
		User gp2LabAdmin = new User("u7");
		User gp2LabAdminViewAll = new User("u8");
		User gp2RegularUser = new User("u9");
		gp2.addMember(gp2Pi, RoleInGroup.PI);
		gp2.addMember(gp2LabAdmin, RoleInGroup.RS_LAB_ADMIN);
		gp2.addMember(gp2LabAdminViewAll, RoleInGroup.RS_LAB_ADMIN);
		gp2.addMember(gp2RegularUser, RoleInGroup.DEFAULT);
		enableViewAll(gp2, gp2LabAdminViewAll);

		// No one can see PIs work
		assertEquals(0, gp1Pi1.getGroupMembersWithViewAll().size());
		assertEquals(0, gp1Pi1.getGroupMembersWithViewAll(gp1).size());

		// PIs and Lab Admins with view all can see regular member's and lab admin work
		assertEquals(3, gp1RegularUser.getGroupMembersWithViewAll().size());
		assertEquals(3, gp1LabAdmin.getGroupMembersWithViewAll(gp1).size());

		// Test with multiple groups
		assertEquals(0, user.getGroupMembersWithViewAll().size());
		gp1.addMember(user);
		assertEquals(3, user.getGroupMembersWithViewAll().size());
		assertEquals(3, user.getGroupMembersWithViewAll(gp1).size());
		gp2.addMember(user);
		assertEquals(3, user.getGroupMembersWithViewAll(gp1).size());
		assertEquals(2, user.getGroupMembersWithViewAll(gp2).size());
		assertEquals(5, user.getGroupMembersWithViewAll().size());
	}

	private void enableViewAll(Group group, User user) {
		UserGroup ug = group.getUserGroupForUser(user);

		Set<UserGroup> ugs = group.getUserGroups();
		ugs.remove(ug);

		ug.setAdminViewDocsEnabled(true);
		ugs.add(ug);

		group.setUserGroups(ugs);
	}

	@Test
	public void testTagsSaveRetrieval() {
		User user = new User("u1");
		assertEquals(List.of(), user.getTagsList());

		List<String> testTags = List.of("tagA", "tagB:C,D", "tagE");
		user.setTagsList(testTags);
		assertEquals("[\"tagA\",\"tagB:C,D\",\"tagE\"]", user.getTagsJsonString());
		assertEquals(testTags, user.getTagsList());
	}

	@Test
	public void saveUsernameAlias() {
		u1.setUsernameAlias("u1");
		assertEquals("u1", u1.getUsernameAlias());
		u1.setUsernameAlias(" ");
		assertNull(u1.getUsernameAlias(), "empty/blank alias expected to be saved as null");
	}


}
