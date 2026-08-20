package com.researchspace.model.record;

import static com.researchspace.model.record.TestFactory.createAFolder;
import static com.researchspace.model.record.TestFactory.createAnyGroup;
import static com.researchspace.model.record.TestFactory.createAnyUserWithRole;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;

/**
 * Based on Folder Test as Notebook is subclass of folder. Created as a separate class because not all
 * operations are supported in notebook and in future notebook may need more operations.
 */
public class NotebookTest {

	private Notebook notebook, nb3;
	private Folder folder1, folder2;
	private StructuredDocument sd; 
	private User anyuser = TestFactory.createAnyUser("any");
	
	@BeforeEach
	public void setUp() {
		notebook = TestFactory.createANotebook("any", anyuser);
		sd = TestFactory.createAnySD();
		sd.setOwner(anyuser);
		notebook.setOwner(sd.getOwner());
	}
	
	@AfterEach
	public void tearDown() {
	}
	
	@Test
	public void dateTimeNotNullAfterCreation() {
		assertNotNull(notebook.getCreationDate());
		assertNotNull(notebook.getEditInfo().getCreationDateMillis());
	}
	
	@Test
	public void addNotebookToFolder() throws IllegalAddChildOperation {
		Folder f = TestFactory.createAFolder("any", anyuser);
		assertEquals(0, f.getChildren().size());
		sleep();
		f.addChild(notebook, anyuser, true);
		assertEquals(f, notebook.getParent());
	}

	protected void sleep() {
		try {
			Thread.sleep(1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@Test
	public void copyNotebook() throws InterruptedException, IllegalAddChildOperation {
		
		folder1 = TestFactory.createANotebook("any", anyuser);
		folder1.setDocTag("notebook tag");
		StructuredDocument t1Doc = TestFactory.createAnySD(TestFactory.createAnyForm());
		folder1.addChild(t1Doc, anyuser);

		Notebook t1Copy = (Notebook) folder1.copy(anyuser, true);
		assertNotNull(folder1);
		assertNotNull(t1Copy);
		assertEquals("notebook tag", t1Copy.getDocTag());
		assertEquals(1, t1Copy.getChildren().size());
	}
	
	@Test
	public void removeRecordFromNotebook() throws IllegalAddChildOperation {
		assertEquals(0, notebook.getChildren().size());
		StructuredDocument c1 = TestFactory.createAnySD(TestFactory.createAnyForm());
		notebook.addChild(c1, anyuser);
		assertEquals(1, notebook.getChildren().size());
		notebook.removeChild(c1);
		assertNull(c1.getParent());
		assertEquals(0, notebook.getChildren().size());
	}
	
	@Test
	public void notebookIsNotebook() {
		Notebook n = TestFactory.createANotebook("any", anyuser);
		assertTrue(n.hasType(RecordType.NOTEBOOK));
		assertTrue(n.isNotebook());
	}

	//RSPAC1814
	@Test
	public void moveOutOfNotebookRequiresSameOwner() {
		notebook.addChild(sd, anyuser);
		folder1 = createAFolder("any", anyuser);
		assertTrue(sd.move(notebook, folder1, anyuser));
		assertEquals(0, notebook.getChildrens().size());
		assertEquals(1, folder1.getChildrens().size());
	}
	
	@Test
	public void moveOutOfNotebookRequiresSameOwnerFailsIfNot() {
		notebook.addChild(sd, anyuser);
		folder1 = createAFolder("any", anyuser);
		//owner of notebook is not the same as document in owner
		sd.setOwner(TestFactory.createAnyUser("other"));
		assertFalse(sd.move(notebook, folder1, anyuser));
		assertEquals(1, notebook.getChildrens().size());
		assertEquals(0, folder1.getChildrens().size());		
	}

	@Test
	public void moveTo() throws InterruptedException, IllegalAddChildOperation {
		makeNestedFolders();
		Folder old = nb3.getParent();
		assertTrue(nb3.move(nb3.getParent(), folder1, anyuser)); // OK
		assertFalse(nb3.move(old, folder1, anyuser)); // not OK, is already in t1
	}
	
	@Test
	public void isChildOf() throws InterruptedException {
		 makeNestedFolders();
		 assertTrue(folder2.isDescendantOf(folder1));
		 assertTrue(nb3.isDescendantOf(folder2));
		 assertFalse(folder1.isDescendantOf(folder2));
		 //cannot be child of itself
		 assertFalse(folder2.isDescendantOf(folder2));	
	}
	
	@Test
	public void cantAddFolderToNotebook() {
		Folder f  = TestFactory.createAFolder("any", anyuser);
	  assertThrows(IllegalAddChildOperation.class, ()->notebook.addChild(f, anyuser, true));
	}
	
	/**
	 * Makes a 3-deep nested folder structure third record being a notebook with a record inside
	 * t1 /t2/t3/rtd
	 */
	 private Folder makeNestedFolders() throws InterruptedException {
		folder1 = TestFactory.createAFolder("level1", anyuser);
		Thread.sleep(1);
		folder2 = TestFactory.createAFolder("level2", anyuser);
		Thread.sleep(1);
		nb3 = TestFactory.createANotebook("level3", anyuser);
		Thread.sleep(1);
		try {
			nb3.addChild(sd, anyuser);
			folder2.addChild(nb3, anyuser);
			folder1.addChild(folder2, anyuser);
		} catch (IllegalAddChildOperation e) {
			e.printStackTrace();
		}
		return folder1;
	}
	 
	 @Test
	 void isAutosharable () {
		Group g = createAnyGroup(createAnyUserWithRole("anyPI", Constants.PI_ROLE), notebook.getOwner());
		assertFalse(notebook.isAutosharable());
		g.getUserGroupForUser(notebook.getOwner()).setAutoshareEnabled(true);
		assertTrue(notebook.isAutosharable());
	}

}
