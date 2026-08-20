package com.researchspace.model.record;

import static com.researchspace.model.record.Folder.SHARED_FOLDER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;

public class FolderTest {

	private Folder f, t1, t2, t3;
	private StructuredDocument sd;
	private Snippet snippet;
	private User anyuser = TestFactory.createAnyUser("any");

	@BeforeEach
	public void setUp() throws Exception {
		f = new Folder();
		t1 = t2 = t3 = null;
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void dateTimeNotNullAfterCreation() {
		assertNotNull(f.getCreationDate());
		assertNotNull(f.getEditInfo().getCreationDateMillis());
	}

	@Test
	public void addRecordToChildren() throws IllegalAddChildOperation {
		assertEquals(0, f.getChildren().size());
		sleep(1);
		Folder c1 = new Folder();
		f.addChild(c1, anyuser);
		assertEquals(1, f.getChildren().size());
		assertEquals(f, c1.getParent());
	}

	@Test
	public void getGenerateCycleAttemptThrowsException() throws IllegalAddChildOperation {
		Folder f2 = TestFactory.createAFolder("1", anyuser);
		Folder f3 = TestFactory.createAFolder("1", anyuser);
		f2.addChild(f3, anyuser, true);
		assertThrows(IllegalAddChildOperation.class, ()->f3.addChild(f2, anyuser, true));
	}

	private void sleep(int i) {
		try {
			Thread.sleep(i);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void removeRecord() throws IllegalAddChildOperation {
		assertEquals(0, f.getChildren().size());
		sleep(1);
		Folder c1 = new Folder();
		f.addChild(c1, anyuser);
		assertEquals(1, f.getChildren().size());
		f.removeChild(c1);
		assertNull(c1.getParent());
		assertEquals(0, f.getChildren().size());
	}

	@Test
	public void copyFolder() throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		t2.setDocTag("test t2 tags");
		sleep(1);
		Folder t1Copy = t1.copy(anyuser, true);
		sleep(1);
		Folder t2Copy = t1Copy.getSubfolders().iterator().next();
		Folder t3Copy = t2Copy.getSubfolders().iterator().next();
		assertNotNull(t2Copy);
		assertEquals(t2.getDocTag(), t2Copy.getDocTag());
		assertNotNull(t3Copy);
		assertEquals(2, t3Copy.getChildrens().size());
	}

	@Test
	public void isChildOf() throws InterruptedException {
		makeNestedFolders();
		assertTrue(t2.isDescendantOf(t1));
		assertTrue(t3.isDescendantOf(t2));
		assertFalse(t1.isDescendantOf(t2));
		// cannot be child of itself
		assertFalse(t2.isDescendantOf(t2));
	}

	@Test
	public void moveToThrowsExceptionOnMoveToSelf() throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		assertThrows( IllegalAddChildOperation.class, ()->t2.move(t2.getSingleParent(), t2, anyuser));
	}

	@Test
	public void moveToThrowsExceptionOnMoveToChildOfSelf() throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		assertThrows( IllegalAddChildOperation.class, ()->t2.move(t2.getSingleParent(), t2, anyuser));// cannot move
																// to child
	}

	@Test
	public void moveToReturnsFalseIfTargetAlreadyHoldsRecord()
			throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		Folder old = t3.getParent();
		assertTrue(t3.move(t3.getParent(), t1, anyuser)); // OK
		assertFalse(t3.move(old, t1, anyuser)); // not OK, is already in t1
		assertFalse(t3.move(null, null, null)); // no null args
	}

	@Test
	public void moveToReturnsFalseIfMovingSharedFoldersOutOfShared() {
		Folder workspaceRoot = new Folder();
		workspaceRoot.addType(RecordType.ROOT);
		Folder workspaceSubfolder = new Folder();

		Folder sharedFolder = new Folder();
		sharedFolder.setSystemFolder(true);
		sharedFolder.setName(SHARED_FOLDER_NAME);
		Folder sharedGroupFolder = new Folder();
		sharedGroupFolder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
		Folder sharedGroupSubFolder = new Folder();
		sharedGroupSubFolder.addType(RecordType.SHARED_FOLDER);

		workspaceRoot.addChild(workspaceSubfolder, anyuser);
		workspaceRoot.addChild(sharedFolder, anyuser);
		sharedFolder.addChild(sharedGroupFolder, anyuser);
		sharedGroupFolder.addChild(sharedGroupSubFolder, anyuser);

		// shared folders of various types shouldn't be movable out of Workspace->Shared hierarchy
		assertFalse(sharedFolder.move(workspaceRoot, workspaceSubfolder, anyuser));
		assertFalse(sharedGroupFolder.move(sharedFolder, workspaceSubfolder, anyuser));
		assertFalse(sharedGroupSubFolder.move(sharedGroupFolder, workspaceSubfolder, anyuser));
	}

	@Test
	public void isRoot() throws IllegalAddChildOperation {
		Folder f = new Folder();
		assertFalse(f.isRootFolder());
		f.addType(RecordType.ROOT);
		assertTrue(f.isRootFolder());

		Folder newRoot = new Folder();
		newRoot.setOwner(anyuser);
		f.setOwner(anyuser);
		newRoot.addChild(f, anyuser, true);
		newRoot.addType(RecordType.ROOT);
		assertTrue(f.isRootFolder()); // must can be root for a user
		assertTrue(newRoot.isRootFolderForUser(anyuser));
		assertFalse(f.isRootFolderForUser(TestFactory.createAnyUser("another")));
	}
	
	@Test
	public void globalIdPrefix_RSPAC842() throws IllegalAddChildOperation {
		Folder anyFolder = TestFactory.createAFolder("any", anyuser);
		assertEquals(GlobalIdPrefix.FL, anyFolder.getGlobalIdPrefix());
		
		Folder mediaRoot = TestFactory.createAFolder("media", anyuser);
		mediaRoot.addType(RecordType.ROOT_MEDIA);
		mediaRoot.addChild(anyFolder, anyuser, true);
		assertEquals(GlobalIdPrefix.GF, anyFolder.getGlobalIdPrefix());
	}

	@Test
	public void isTemplateFolder() throws IllegalAddChildOperation {
		Folder folder = new Folder();
		assertFalse(folder.isTemplateFolder());
		folder.addType(RecordType.TEMPLATE);
		assertFalse(folder.isRootFolder());
		folder.setSystemFolder(true);
		assertFalse(folder.isRootFolder());
		folder.setName(Folder.TEMPLATE_MEDIA_FOLDER_NAME);
		// all must be set
		assertTrue(folder.isTemplateFolder());
		folder.setSystemFolder(false);
		assertFalse(folder.isRootFolder());
		assertFalse(folder.isApiInboxFolder());
	}
	
	@Test
	public void isSharedFolder() throws IllegalAddChildOperation {
		Folder folder = new Folder();
		assertFalse(folder.isSharedFolder());
		folder.addType(RecordType.SHARED_FOLDER);
		assertTrue(folder.isSharedFolder());
		folder.removeType(RecordType.SHARED_FOLDER);
		folder.addType(RecordType.SHARED_GROUP_FOLDER_ROOT);
		assertTrue(folder.isSharedFolder());
		folder.removeType(RecordType.SHARED_GROUP_FOLDER_ROOT);
		folder.addType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
		assertTrue(folder.isSharedFolder());
		folder.removeType(RecordType.INDIVIDUAL_SHARED_FOLDER_ROOT);
		assertFalse(folder.isSharedFolder());
		assertFalse(folder.isApiInboxFolder());
	}

	@Test
	public void addToSharedFolder() throws IllegalAddChildOperation {
		Folder sharedFolder = new Folder();
		sharedFolder.setSystemFolder(true);
		sharedFolder.setName(SHARED_FOLDER_NAME);
		User user = TestFactory.createAnyUser("us");
		Folder root = TestFactory.createAFolder("root", user);
		root.addType(RecordType.ROOT);
		root.addChild(sharedFolder, user, true);
		Folder added = sharedFolder.addChild(TestFactory.createAnySD(), user, true).getFolder();
		assertEquals(added, sharedFolder);
	}

	@Test
	public void addToFolderTemplateACL() throws IllegalAddChildOperation {
		Folder templateFolder = TestFactory.createTemplateFolder(anyuser);
		Folder newFolder = TestFactory.createAFolder("any", anyuser);
		assertFalse(newFolder.getSharingACL().isPermitted(anyuser, PermissionType.DELETE));
		templateFolder.addChild(newFolder, anyuser, true);
		assertTrue(newFolder.getSharingACL().isPermitted(anyuser, PermissionType.DELETE));
	}

	@Test
	public void processor() throws InterruptedException {
		TestProcessor processor = new TestProcessor();
		makeNestedFolders();
		t1.process(processor);
		assertEquals(3, processor.f_count);
		assertEquals(1, processor.rec_count);
	}
	@Test
	public void defaultACLPRopagationPolicyDoesNotPropagatesAnonymousShareAcl() throws InterruptedException {
		makeNestedFolders();
		Record record = TestFactory.createAnySD();
		t3.addChild(record, anyuser, true);
		Folder newRoot = TestFactory.createAFolder("newParent", anyuser);
		ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
		ACLElement anonymousSharedElement = new ACLElement(
				RecordGroupSharing.ANONYMOUS_USER,
				parser.resolvePermission("RECORD:READ:"));
		newRoot.getSharingACL().addACLElement(anonymousSharedElement);
		newRoot.addChild(t1, anyuser, true);
		for(ACLElement el : t1.getSharingACL().getAclElements()){
			assertFalse(el.getUserOrGrpUniqueName().equals(RecordGroupSharing.ANONYMOUS_USER));
		}
	}
	@Test
	public void defaultACLPRopagationPolicyPropagatesACLsToALlChildren()
			throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		Record record = TestFactory.createAnySD();
		t3.addChild(record, anyuser);
		Folder newRoot = TestFactory.createAFolder("newParent", anyuser);

		// test ADDITION
		ConstraintBasedPermission perm = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);
		newRoot.getSharingACL().addACLElement(anyuser, perm);
		assertTrue(newRoot.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		// t3 does not have this permission yet..
		assertFalse(t3.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		// until it is added to root - by default, inherit-from-parent policy
		// isused.
		newRoot.addChild(t1, anyuser);
		assertTrue(t3.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		assertTrue(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		// check records as well as folders are set
		assertTrue(record.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));

		// on REmoval - remove all ACLS inheriting from the old parent
		t1.removeChild(t2);
		assertTrue(t1.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		assertFalse(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
		assertFalse(record.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
	}

	@Test
	public void moreComplicatedDefaultACLPRopagationPolicyPropagatesACLsToALlChildren()
			throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();

		ConstraintBasedPermission perm = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.WRITE);

		Folder newRoot1 = TestFactory.createAFolder("newParent1", anyuser);
		newRoot1.getSharingACL().addACLElement(anyuser, perm);
		newRoot1.addChild(t2, anyuser);

		User other = TestFactory.createAnyUser("other");
		Folder newRoot2 = TestFactory.createAFolder("newParent2", other);
		newRoot2.getSharingACL().addACLElement(other, perm);
		newRoot2.addChild(t2, other);

		// t2 has permissions from both newRoot and newRoot2
		assertEquals(2, t2.getSharingACL().getNumPermissions());
		assertTrue(t2.getSharingACL().isPermitted(other, PermissionType.WRITE));
		assertTrue(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));

		// now remove t2 from newparent2 - this should remove only newParent2's
		// permissions
		newRoot2.removeChild(t2);
		assertFalse(t2.getSharingACL().isPermitted(other, PermissionType.WRITE));
		assertTrue(t2.getSharingACL().isPermitted(anyuser, PermissionType.WRITE));
	}

	@Test
	public void getAllAncestors() throws InterruptedException, IllegalAddChildOperation {
		Folder root = makeNestedFolders();
		List<Folder> folders = sd.getAllAncestors();
		assertEquals(3, folders.size());
		Folder x = TestFactory.createAFolder("parent2", anyuser);
		x.addChild(sd, anyuser, true);
		assertEquals(4, sd.getAllAncestors().size());
		
		// create artificial cycle in folder structure, ensure that getAllAncestors can handle it
		folders.get(0).doAddToParentsOnly(root, anyuser);
		assertEquals(4, sd.getAllAncestors().size());
	}

	@Test
	public void getAncestorByType() throws InterruptedException, IllegalAddChildOperation {
		Folder root = makeNestedFolders();
		assertFalse(t3.hasAncestorOfType(RecordType.ROOT, true));
		root.addType(RecordType.ROOT);
		assertTrue(t3.hasAncestorOfType(RecordType.ROOT, true));
		t2.addType(RecordType.NOTEBOOK);
		assertTrue(t3.hasAncestorOfType(RecordType.NOTEBOOK, true));
		// include calling object in type comparison or not?
		assertTrue(t2.hasAncestorOfType(RecordType.NOTEBOOK, true));
		assertFalse(t2.hasAncestorOfType(RecordType.NOTEBOOK, false));
		// check top of hierarchy is handled OK
		assertFalse(root.hasAncestorOfType(RecordType.ROOT, false));
	}

	@Test
	public void clearACLs() throws InterruptedException {
		// setup
		Folder root = TestFactory.createAFolder("root", anyuser);
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ,
				PermissionType.COPY);
		root.getSharingACL().addACLElement(new ACLElement(anyuser.getUsername(), cbp));
		t1 = makeNestedFolders();
		root.addChild(t1, anyuser);
		// assert
		assertTrue(t3.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		assertTrue(root.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		root.clearACL(false);
		assertFalse(root.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		assertTrue(t3.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		assertTrue(sd.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		root.clearACL(true);
		assertFalse(t3.getSharingACL().isPermitted(anyuser, PermissionType.READ));
		assertFalse(sd.getSharingACL().isPermitted(anyuser, PermissionType.READ));
	}
	
	
	@Test
	public void isApiFolder() {
		Folder apiFolder = TestFactory.createAnAPiInboxFolder(TestFactory.createAnyUser("any"));
		assertTrue(apiFolder.isApiInboxFolder());
		apiFolder.removeType(RecordType.API_INBOX);
		assertFalse(apiFolder.isApiInboxFolder());
	}
	
	@Test
	public void isImportsFolder() {
		Folder importsFolder = TestFactory.createAnImportsFolder(TestFactory.createAnyUser("any"));
		assertTrue(importsFolder.isImportsFolder());
		importsFolder.removeType(RecordType.IMPORTS);
		assertFalse(importsFolder.isImportsFolder());
	}
	
	@Test
	@DisplayName("RSPAC-1543: children of Import folder have standard actions permitted ")
	public void addChildToImportFolder (){
		StructuredDocument child = TestFactory.createAnySD();
		User subject = child.getOwner();
		Folder importsFlder = TestFactory.createAnImportsFolder(subject);
		new DefaultPermissionFactory().setUpAclForIndividualInboxFolder(importsFlder, subject);
		importsFlder.addChild(child, subject, true);
		assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.COPY));
		assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.DELETE));
		assertTrue(child.getSharingACL().isPermitted(subject, PermissionType.RENAME));
	}
	
	@Test
	public void deletedDate() {
		// initially null - not deleted
		assertNull(f.getDeletedDate());
		
		// check date is now. more or less
		f.setRecordDeleted(true);
		Date stored = f.getDeletedDate();
		assertNotNull(stored);
		// assert deletion date is now
		assertEquals(DateUtils.ceiling(stored, Calendar.MINUTE),
				DateUtils.ceiling(new Date(), Calendar.MINUTE) );
		
		// after undeleting, deletion date is now null again.
		f.setRecordDeleted(false);
		assertNull(f.getDeletedDate());
	}
	

	@Test
	public void cleanPermissionsResetOnMove() {
		Folder root1Src = TestFactory.createAFolder("rootSrc", anyuser);
		Folder root2Dest = TestFactory.createAFolder("rootDest", anyuser);
		Folder toMove = TestFactory.createAFolder("toMove", anyuser);
		Folder toMoveChild = TestFactory.createAFolder("toMove", anyuser);
		Folder toMoveGrandChild = TestFactory.createAFolder("toMove", anyuser);
		addPermissionTo(root1Src, PermissionType.COPY, anyuser);
		addPermissionTo(root2Dest, PermissionType.EXPORT, anyuser);
		addPermissionTo(toMove, PermissionType.SHARE, anyuser);
		root1Src.addChild(toMove, anyuser);
		toMoveChild.addChild(toMoveGrandChild, anyuser);
		toMove.addChild(toMoveChild, anyuser);

		assertTrue(toMove.getSharingACL().isPermitted(anyuser, PermissionType.COPY));
		assertTrue(toMove.getSharingACL().isPermitted(anyuser, PermissionType.SHARE));

		toMove.move(root1Src, root2Dest, anyuser);
		toMove.clearACL(true);
		addPermissionTo(toMove, PermissionType.CREATE, anyuser);
		ACLPropagationPolicy.DEFAULT_POLICY.onAdd(toMove, toMoveChild);
		
		
		assertFalse(toMove.getSharingACL().isPermitted(anyuser, PermissionType.COPY),"Should lose permissions of src parent folder");
		assertFalse(toMove.getSharingACL().isPermitted(anyuser, PermissionType.EXPORT),"Shouldn't acquire permissions of dest parent folder");
		assertFalse( toMove.getSharingACL().isPermitted(anyuser, PermissionType.SHARE),"Should lose original perms");
		assertTrue( toMove.getSharingACL().isPermitted(anyuser, PermissionType.CREATE),"Should have create permission");
		assertTrue(toMoveChild.getSharingACL().isPermitted(anyuser, PermissionType.CREATE),"Child folder should inherit permission");
		assertTrue(toMoveGrandChild.getSharingACL().isPermitted(anyuser, PermissionType.CREATE),"grandchild folder should inherit permission");

	}

	private void addPermissionTo(Folder folder, PermissionType permType, User user) {
		ConstraintBasedPermission cbp = new ConstraintBasedPermission(PermissionDomain.RECORD, permType);
		folder.getSharingACL().addACLElement(new ACLElement(user.getUsername(), cbp));

	}

	/**
	 * Makes a 3-deep nested folder structure with a record inside t1 /t2/t3/rtd
	 */
	private Folder makeNestedFolders() throws InterruptedException {
		t1 = TestFactory.createAFolder("level1", anyuser);
		t2 = TestFactory.createAFolder("level2", anyuser);
		t3 = TestFactory.createAFolder("level3", anyuser);
		sd = TestFactory.createAnySD();
		sd.setOwner(anyuser);
		snippet = TestFactory.createAnySnippet(anyuser);
		try {
			t3.addChild(sd, anyuser);
			t3.addChild(snippet, anyuser);
			t2.addChild(t3, anyuser);
			t1.addChild(t2, anyuser);
		} catch (IllegalAddChildOperation e) {
			e.printStackTrace();
		}

		Thread.sleep(1);
		return t1;
	}

}

class TestProcessor implements RecordContainerProcessor {
	int rec_count = 0;
	int f_count = 0;

	public boolean process(BaseRecord rc) {
		System.err.println(rc.getName());
		if (rc.isFolder()) {
			f_count++;
		}
		if (rc.isStructuredDocument()) {
			rec_count++;
		}
		return true;
	}
}
