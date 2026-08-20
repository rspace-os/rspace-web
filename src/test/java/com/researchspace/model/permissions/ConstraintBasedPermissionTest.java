package com.researchspace.model.permissions;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import com.researchspace.Constants;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class ConstraintBasedPermissionTest {

	ConstraintBasedPermission userPermission;
	
	EntityPermission docPermission;
	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testImpliesReturnsTrueIfPermsTheSame() {
		Set<PermissionType> readWRite = setUpPermissions(PermissionType.READ);
		createReadWriteEntityPermssion();
		assertTrue (userPermission.isEnabled());
		assertTrue(userPermission.implies(docPermission));
		
		
		// by default will be enabled
		userPermission.setEnabled(false);
		assertFalse(userPermission.implies(docPermission));
		
	}

	public void createReadWriteEntityPermssion() {
		docPermission = new EntityPermission(PermissionDomain.RECORD, PermissionType.READ);
	}
	
	@Test
	public void testCompare() {
		userPermission  = new ConstraintBasedPermission();
		userPermission.setDomain(PermissionDomain.RECORD);
		
		ConstraintBasedPermission userPermission2  = new ConstraintBasedPermission();
		userPermission2.setDomain(PermissionDomain.FORM);
	    List<ConstraintBasedPermission>	toSort = new ArrayList<>();
	    // check based on domain
	    toSort.add(userPermission2);
	    toSort.add(userPermission);
	    Collections.sort(toSort);
	    assertEquals(userPermission, toSort.get(0));
	    
	    // now set domains the same, alter type
	    userPermission2.setDomain(PermissionDomain.RECORD);
	    userPermission2.addPermissionType(PermissionType.READ);
	    userPermission.addPermissionType(PermissionType.WRITE);
	    
	    Collections.sort(toSort);
	    assertEquals(userPermission2, toSort.get(0));
	
	}
	
	@Test
	public void testImpliesReturnsFalseIdNotTestingEntityPermission() {
		Set<PermissionType> readWRite = setUpPermissions();
		createReadWriteEntityPermssion();
		assertFalse(userPermission.implies(userPermission));
	}
	
	@Test
	public void testPermissionDomainMatch() {
		Set<PermissionType> readWRite = setUpPermissions(PermissionType.READ);
		docPermission =new EntityPermission(PermissionDomain.FORM, PermissionType.READ);
		assertFalse(userPermission.implies(docPermission));
		
		//can do anything
		userPermission.setDomain(PermissionDomain.ALL);
		assertTrue(userPermission.implies(docPermission));
	}
	
	@Test
	public void testPermissionTypeMatch() {
		Set<PermissionType> userPermRead = setUpPermissions(PermissionType.READ);
//		Set<PermissionType> docREquestPerm = createPermissonActionSet(PermissionType.EXPORT);
		docPermission = new EntityPermission(PermissionDomain.RECORD, PermissionType.EXPORT);
		
		assertFalse(userPermission.implies(docPermission));
		userPermission.addPermissionType(PermissionType.EXPORT);
		assertTrue(userPermission.implies(docPermission));
	}
	
	@Test
	public void testIdDateRangeMatch() {
		Set<PermissionType> userPermRead = setUpPermissions(PermissionType.READ);
		Set<PermissionType> docREquestPerm = createPermissonActionSet(PermissionType.READ);
		createReadWriteEntityPermssion();
		
		Set <Long>ids = new HashSet<>();
		ids.add(3L);
		IdConstraint dc = new IdConstraint( ids);
		userPermission.setIdConstraint(dc);
		docPermission.setId(4L);// no match
		assertFalse(userPermission.implies(docPermission));
		
		docPermission.setId(3L);//matches
		assertTrue(userPermission.implies(docPermission));	
	}
		
	@Test
	public void testPropertyMatch (){
		ConstraintBasedPermission globalCreateUser = new ConstraintBasedPermission(PermissionDomain.USER, PermissionType.CREATE);
		ConstraintBasedPermission toCheck = new ConstraintBasedPermission(PermissionDomain.USER, PermissionType.CREATE);
		toCheck.addPropertyConstraint(new PropertyConstraint("name", "value"));
		assertTrue(globalCreateUser.implies(toCheck));
		//what about the other way round
		// yes, it matches. both have to dealre property constraints to force a match
		assertTrue(toCheck.implies(globalCreateUser));
		
		
	}
	@Test
	public void testPropertyConstraintMatch() {
		Set<PermissionType> userPermRead = setUpPermissions(PermissionType.READ);
		Set<PermissionType> docREquestPerm = createPermissonActionSet(PermissionType.READ);
		createReadWriteEntityPermssion();
		userPermission.addPropertyConstraint(createAPropertyConstraint("ownedBy", "user3"));
		docPermission.addPropertyConstraint(createAPropertyConstraint("ownedBy", "user4"));
		
		assertFalse(userPermission.implies(docPermission));
		docPermission.addPropertyConstraint(createAPropertyConstraint("ownedBy", "user3"));
		assertTrue(userPermission.implies(docPermission));
		
		userPermission.addPropertyConstraint(createAPropertyConstraint("other", "otherval"));
		assertTrue(userPermission.implies(docPermission));
		
		// don't car if document has other properties, we're only comparing properties with the same name
		docPermission.addPropertyConstraint(new PropertyConstraint("docProperty", "value"));
		assertTrue(userPermission.implies(docPermission));
	}
	
	
	@Test
	public void testAssignSelfPermissionsToGroup (){
		userPermission=new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		PropertyConstraintTSS pc=new PropertyConstraintTSS("owner", "${self}");
		pc.uname="u1";
		userPermission.addPropertyConstraint(pc);
		
		// add permission to group
		User u1 = new User("u1");
		Group g1 = new Group("g1",u1);
		g1.addMember(u1, RoleInGroup.RS_LAB_ADMIN);
		g1.addPermission(userPermission);
		
		//create record permission
		Record record = TestFactory.createAnySD();
		record.setOwner(u1);
		RecordPermissionAdapter rpa = new RecordPermissionAdapter(record);
		rpa.setAction(PermissionType.READ);
		
		assertTrue(u1.isPermitted(rpa, true));		
	}
	
	@Test
	public void testMatchCommunityPermission() {
		
		User pi = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
		User admin = TestFactory.createAnyUserWithRole("admini", Constants.ADMIN_ROLE);
		Group g = TestFactory.createAnyGroup(pi, null);
		g.setCommunityId(1L);

		Community comm = new Community();
		comm.addAdmin(admin);
		comm.setUniqueName("comm1");
		comm.addLabGroup(g);
		comm.setId(1L);
		// community admins will have this permission added
		userPermission = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		userPermission.setCommunityConstraint(new CommunityConstraint(comm.getId()));
		Record record = TestFactory.createAnySD();
		record.setOwner(pi);
		RecordPermissionAdapter rpa = new RecordPermissionAdapter(record);
		rpa.setAction(PermissionType.READ);
		assertTrue(userPermission.implies(rpa));

		// set community ID to other ID, no match
		comm.setId(2L);
		g.setCommunityId(2L);
		assertFalse(userPermission.implies(rpa));

		comm.addAdmin(admin);
	}
	//adding large numbers of permissions is slow due to slow equals method and 
	// equals() called on all set members when adding a new one.
	// 2000 perms is when performance degrades, flattenig many permissions into 1 cuts down on number of equals
	// calls as there now less additions to hashset of permissions
	@Test
	public void testImpliesFor2000Permissions(){
		// we can add permissions faster using list as we don't test for duplicates
		List<Permission> allPerms = new ArrayList<>(2000);
		StopWatch sw = StopWatch.createStarted();
		// set up indepenent permissions objects, like 2000 items have been shared individually
		final int NUM_INDIVIDUAL_PERMS = 2000;
		for (long i =0; i< NUM_INDIVIDUAL_PERMS;i++) {
			ConstraintBasedPermission userReadPermission = 
					new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
			if (i % 2 ==0) {
				userReadPermission.setActions(EnumSet.of(PermissionType.READ));
			}
			IdConstraint con = new IdConstraint(Long.MAX_VALUE - i);	
			userReadPermission.setIdConstraint(con);
			allPerms.add(userReadPermission);
		}
	
		TestAuthReal realm = new TestAuthReal();
		SimpleAuthorizationInfo inf = new SimpleAuthorizationInfo();
		inf.addObjectPermissions(allPerms);
		long slowStart = sw.getTime();
		realm.getPermissions(inf);
		long slowEnd = sw.getTime();
		long slowElapsed = slowEnd - slowStart;
	
		
		assertEquals(NUM_INDIVIDUAL_PERMS, allPerms.size());
		new ConstraintPermissionResolver().flattenRecordReadWritePermissions(allPerms);
		final int EXPECTED_PERM_COUNT_AFTER_FLATTENING = 18;
		assertEquals(EXPECTED_PERM_COUNT_AFTER_FLATTENING, allPerms.size());
		
		inf.setObjectPermissions(new HashSet<>(allPerms));
		long speededStart = sw.getTime();
		realm.getPermissions(inf);
		long speededFinish = sw.getTime();
		long speededElapsed = speededFinish - speededStart;
		double speedup = ((double)slowElapsed / (double)speededElapsed);
		System.err.printf("slow - %d ms, speeded - %d ms, speed up = %4.5f%n", slowElapsed, speededElapsed,
				speedup );
		
		final double MINIMUM_OBSERVED_SPEEDUP = 20.0;
		assertTrue( speedup > MINIMUM_OBSERVED_SPEEDUP,"speedup factor was only " +speedup);

	}
	//subclass to get access to getPermissions protected method
	class TestAuthReal extends AuthorizingRealm {
		
		protected Collection<Permission> getPermissions(AuthorizationInfo info){
			return super.getPermissions(info);
		}

		@Override
		protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	@Test
	public void testWriteImpliesReadPErmission(){
		ConstraintBasedPermission userWritePermission = 
				new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
		docPermission =new EntityPermission(PermissionDomain.RECORD, PermissionType.READ);
		assertTrue(userWritePermission.implies(docPermission));
		
		// but check that READ sdoesn't imply write
		ConstraintBasedPermission userREADpermission = 
				new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
		docPermission =new EntityPermission(PermissionDomain.RECORD, PermissionType.WRITE);
		assertFalse(userREADpermission.implies(docPermission));
	}

	public static PropertyConstraint createAPropertyConstraint(String name, String val) {
		return new PropertyConstraint(name,val);
	}

	public Set<PermissionType> setUpPermissions(PermissionType ... pts) {
		Set<PermissionType> readWRite = createPermissonActionSet(pts);
		userPermission = new ConstraintBasedPermission(PermissionDomain.RECORD, readWRite);
		return readWRite;
	}	
	
	public static Set<PermissionType> createPermissonActionSet(PermissionType ... pts){
		Set<PermissionType> rc = new HashSet<>();
		rc.addAll(Arrays.asList(pts));
		return rc;
	}

}
