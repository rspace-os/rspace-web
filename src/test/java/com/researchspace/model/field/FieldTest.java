package com.researchspace.model.field;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.model.record.TestFactory.createEcatAudio;
import static com.researchspace.model.record.TestFactory.createEcatImage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;



public class FieldTest {
	
	private static String BAD_INPUT="Bad";
	User anyUser;
	
	class FieldTemplateTss extends FieldForm {

		private static final long serialVersionUID = 1L;

		@Override
		public FieldForm shallowCopy() {
			return null;
		}

		@Override
		public Field _createNewFieldFromForm() {
			return new FieldTSS(this);
		}

		@Override
		public ErrorList validate(String data) {
			ErrorList el = new ErrorList();
			if(BAD_INPUT.equals(data)) {
				el.addErrorMsg("Error");
			}
			return el;
		}

		@Override
		public String getDefault() {
			return "";
		}

	}
	
	class FieldTSS extends Field {

		private static final long serialVersionUID = 1L;

		private IFieldForm t;

		public FieldTSS(FieldForm template) {
			this.t=template;
		}

		@Override
		public FieldTSS shallowCopy() {
			FieldTSS tss= new FieldTSS(new FieldTemplateTss() );
			copyFields(tss);
			return tss;
		}
	
		public void setFieldForm(IFieldForm fieldTemplate) {
			this.t=fieldTemplate;
		}

		@Override
		protected IFieldForm _getFieldForm() {
			return t;
		}

		@Override
		protected void _setFieldForm(IFieldForm ft) { }

		@Override
		public String getData() {
			return null;
		}

		@Override
		public void setData(String fieldData) { }

		public boolean isMandatoryStateSatisfied() {
			return true;
		}
	}
	
	private Field toTest;

	static Field createFieldTSS() {
		FieldTest ft = new FieldTest();
		FieldTSS fld = ft.new FieldTSS(ft.new FieldTemplateTss());
		fld.setColumnIndex(3);
		fld.setFieldData("Some data");
		fld.setModificationDate(new Date().getTime());
		fld.setName("NAME");
		
		return fld;
	}

	@BeforeEach
	public void setUp() throws Exception {
		toTest = createFieldTSS();
		anyUser = TestFactory.createAnyUser("any");
	}

	@AfterEach
	public void tearDown() throws Exception {
	}

	@Test
	public void testHashCodeEquals() throws InterruptedException {
		Thread.sleep(5); // ensure ms are different
		Field f2 = createFieldTSS();
		assertFalse(f2.hashCode() == toTest.hashCode());
		assertFalse(f2.equals(toTest));
	}
	
	@Test
	public void testValidatingFalseAllowsBadData (){
		FieldForm ft1 = new FieldTemplateTss();
		Field f1 = new FieldTSS(ft1);
		assertTrue(f1.isValidating());
		f1.setValidating(false);
		assertTrue(f1.validate(BAD_INPUT));
		f1.setFieldData(BAD_INPUT);	 // allowed
	}
	
	@Test
	public void testValidatingThrowsIAEforBadInput() {
		FieldForm ft1 = new FieldTemplateTss();
		Field f1 = new FieldTSS(ft1);
		assertTrue(f1.isValidating());
		assertFalse(f1.validate(BAD_INPUT));
		assertIllegalArgumentException(()->f1.setFieldData(BAD_INPUT));
	}
	
	@Test
	public void testSort()  {
		FieldForm ft1 = new FieldTemplateTss();
		ft1.setColumnIndex(1);
		FieldForm ft2 = new FieldTemplateTss();
		ft2.setColumnIndex(2);
		FieldForm ft3 = new FieldTemplateTss();
		ft3.setColumnIndex(0);
		Field f1 = new FieldTSS(ft1);
		Field f2 = new FieldTSS(ft2);
		Field f3 = new FieldTSS(ft3);
		List<Field> toSort = Arrays.asList(new Field[]{f1,f2,f3});
		Collections.sort(toSort);
		
		// ensure is sorted in column index order.
		assertEquals(0, toSort.indexOf(f3));
		assertEquals(1, toSort.indexOf(f1));
		assertEquals(2, toSort.indexOf(f2));
	}

	@Test
	public void testShallowCopy() throws InterruptedException {
		toTest.setId(1L); // fake persistence
		Thread.sleep(10L);
		Field copy = (Field)toTest.shallowCopy();
		assertNull(copy.getId());
		assertTrue(new Date(copy.getModificationDate()).after(
				new Date(toTest.getModificationDate())));
		assertEquals(toTest.getColumnIndex(), copy.getColumnIndex());
		assertEquals(toTest.getName(), copy.getName());
		assertEquals(toTest.getFieldData(), copy.getFieldData());
		assertEquals(toTest.getType(), copy.getType());
	}

   @Test
   public void testIsFieldForm() {
	   assertFalse(toTest.isTextField());
   }

   @Test
   @Disabled("mysteriously started failing on jenkins")
   public void testGetNewListOfTempFields() {
	   
	   ArrayList<Field> fieldList = new ArrayList<>();
	   Field a = new TextField();
	   Field b = new TextField();
	   Field bTemp = new TextField();
	   
	   b.setValidating(false);
	   b.setTempField(bTemp);
	   
	   fieldList.add(a);
	   fieldList.add(b);
	   
	   List<Field> onlyTempFields = Field.getNewListOfTempFields(fieldList, false);
	   assertEquals( 1, onlyTempFields.size(),"list should include only temp fields");
	   assertEquals( bTemp, onlyTempFields.get(0),"only temp field");
	   
	   List<Field> tempAndNotTempFields = Field.getNewListOfTempFields(fieldList, true);
	   assertEquals( 2, tempAndNotTempFields.size(),"list should include also non-temp fields");
	   assertEquals( a, tempAndNotTempFields.get(0),"first non-temp field");
	   assertEquals( bTemp, tempAndNotTempFields.get(1), "second temp field");
   }
   
   @Test
   public void testGetTempFieldsModifiedAfter() {
	   
	   ArrayList<Field> fieldList = new ArrayList<>();
	   Field a = new TextField();
	   Field b = new TextField();
	   Field bTemp = new TextField();
	   
	   fieldList.add(a);
	   fieldList.add(b);

	   bTemp.setModificationDate(7L);
	   b.setValidating(false);
	   b.setTempField(bTemp);
	   
	   List<Field> tempFieldModifiedAfter5 = Field.getTempFieldsModifiedAfter(fieldList, 5L);
	   assertEquals( 1, tempFieldModifiedAfter5.size(), "should be one field modified after 5");

	   List<Field> tempFieldsModifiedAfter10 = Field.getTempFieldsModifiedAfter(fieldList, 10L);
	   assertEquals( 0, tempFieldsModifiedAfter10.size(), "should be no fields modified after 10");
   }

   @Test
   public void testContainsTempField() {
	   
	   ArrayList<Field> fieldList = new ArrayList<>();
	   Field a = new TextField();
	   fieldList.add(a);

	   assertFalse(Field.listContainsTempField(fieldList));
	   
	   Field b = new TextField();
	   Field bTemp = new TextField();
	   b.setValidating(false);
	   b.setTempField(bTemp);
	   fieldList.add(b);
	   
	   assertTrue(Field.listContainsTempField(fieldList));
   }
   
   @Test
   @DisplayName(" adding media link - assert associations")
   public void addMediaItem() {
	   Field anyField = createFieldTSS();
	   EcatImage image = TestFactory.createEcatImage(2L);
	   Optional<FieldAttachment> fa = anyField.addMediaFileLink(image);
	   assertTrue(fa.isPresent());
	   assertFalse(fa.get().isDeleted());
	   assertEquals(anyField, fa.get().getField());
	   assertEquals(image, fa.get().getMediaFile());
	   assertEquals(image,anyField.getLinkedMediaFiles().iterator().next().getMediaFile());
	   assertEquals(anyField, image.getLinkedFields().iterator().next().getField());
	   
	   // not added twice
	   assertFalse(anyField.addMediaFileLink(image).isPresent());
	   assertEquals(1,anyField.getLinkedMediaFiles().size());
	   assertEquals(1, image.getLinkedFields().size());
   }
   
   @Test
   @DisplayName("mark media link deleted")
   public void setMediaItemDeleted() {
	   //set up a field-media association
	   Field anyField = createFieldTSS();
	   EcatImage image = createEcatImage(2L);
	   Optional<FieldAttachment> fa = anyField.addMediaFileLink(image);
	   
	   // mark deleted and 
	   assertFalse(fa.get().isDeleted());
	   Optional<FieldAttachment> markedRemoved = anyField.setMediaFileLinkDeleted(image, true);
	   assertTrue(markedRemoved.get().isDeleted());
	   Optional<FieldAttachment> restored = anyField.setMediaFileLinkDeleted(image, false);
	   assertFalse(restored.get().isDeleted());
	   
	   // trying to mark a non-existent association just returns empty optional
	   EcatAudio nonAssociatedFile = createEcatAudio(3L, anyUser);
	   assertFalse(anyField.setMediaFileLinkDeleted(nonAssociatedFile, true).isPresent());
   }
   
   @Test
   @DisplayName(" adding media link to deleted link restores the association ")
   public void restoreMediaItem() {
	   Field anyField = createFieldTSS();
	   EcatImage image = TestFactory.createEcatImage(2L);
	   Optional<FieldAttachment> fa = anyField.addMediaFileLink(image);
	   anyField.setMediaFileLinkDeleted(image, true);
	   assertTrue(fa.get().isDeleted());
	   // now add again
	   anyField.addMediaFileLink(image); 
	   assertFalse(fa.get().isDeleted(), "adding file again to a deleted association should un-delete it");
   }
   
   @Test
   @DisplayName("remove media link ")
   public void removeMediaItem() {
	   Field anyField = createFieldTSS();
	   EcatImage image = TestFactory.createEcatImage(2L);
	   EcatVideo anotherFile = TestFactory.createEcatVideo(2L);
	   Optional<FieldAttachment> faImage = anyField.addMediaFileLink(image);
	   Optional<FieldAttachment> faAnotherFile = anyField.addMediaFileLink(anotherFile);
	   
	   //assert that onnly image association is removed
	   FieldAttachment removed =  anyField.removeMediaFileLink(image).get();
	   assertNull(removed.getField());
	   assertNull(removed.getMediaFile());
	   assertEquals(1,anyField.getLinkedMediaFiles().size());
	   assertEquals(0, image.getLinkedFields().size());
	   assertEquals(1, anotherFile.getLinkedFields().size());
	   // trying to remove non-associated file returns empty()
	   EcatAudio nonAssociatedFile = createEcatAudio(3L, anyUser);
	   assertFalse(anyField.removeMediaFileLink(nonAssociatedFile).isPresent());
	   
	// trying to remove image again returns empty()
	   assertFalse(anyField.removeMediaFileLink(image).isPresent());
   }
   
}
