package com.researchspace.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.FileProperty;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.model.record.TestFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.Test;

/**
 * Tests lucene indexing and search.
 * 
 */
class HibernateSearchTest extends HibernateTest {

	@SuppressWarnings("unchecked")
	@Test
	public void searchSamples() throws InterruptedException, IOException {

		// save three samples
		Sample sample = TestFactory.createBasicSampleInContainer(testUser);
		sample.setName("uniquename one");
		sample.setTags("tag1, tag2, tag3");
		sample.setDescription("description1");
		
		Sample sample2 = TestFactory.createBasicSampleInContainer(testUser);
		sample2.setName("uniquename two");
		sample2.setTags("tag4, tag5, tag6");
		sample2.setDescription("description2");
		ExtraTextField extraField = TestFactory.createExtraTextField("extraField", testUser, sample2);
		final String testExtraFieldData = "extrafielddata";
		extraField.setData(testExtraFieldData);
		sample2.addExtraField(extraField);
		FileProperty fp =  TestFactory.createAnyTransientFileProperty(testUser);
		String testAttachmentName = "myAttachment.txt";
		InventoryFile invFile = rf.createInventoryFile(testAttachmentName, fp, testUser);
		sample2.addAttachedFile(invFile);
		
		Sample sample3 = TestFactory.createComplexSampleInContainer(testUser);
		sample3.setName("uniquename three");
		sample3.setTags("tag7, tag8, tag9");
		sample3.setDescription("description3");
		sample3.setSharingACL(new RecordSharingACL());
		sample3.getSharingACL().addACLElement(new ACLElement("group1", new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.CREATE)));
		final String testFieldData = "textfielddata";
		sample3.getActiveFields().get(5).setData(testFieldData);
		sample3.addBarcode(new Barcode("Barcode123", null));

		sample = saveSampleInContainer(sample);
		saveAssociatedEntities(sample2);
		sample2 = saveSampleInContainer(sample2);
		sample3 = saveComplexSample(sample3);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			// build lucene index and query
			SearchSession searchSession = Search.session(sf.getCurrentSession());
			searchSession.massIndexer().startAndWait();

			// try various searches
			
			// search that doesn't match any sample
			List<Sample> samples = searchSession.search(Sample.class)
					.where(f -> f.match().fields("name", "name").matching("asdf"))
					.fetchHits(20);

			assertNotNull(samples);
			assertEquals(0, samples.size());

			// find by name
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("one"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename one", samples.get(0).getName());

			// by tag
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("tag4"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename two", samples.get(0).getName());

			// by description
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("description3"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename three", samples.get(0).getName());
			
			// by field content
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("fields.fieldData").matching(testFieldData))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename three", samples.get(0).getName());
			
			// by extra field content
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("extraFields.fieldData").matching(testExtraFieldData))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename two", samples.get(0).getName());

			// by attachment name
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("files.fieldData").matching(testAttachmentName))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename two", samples.get(0).getName());
			assertEquals(1, samples.get(0).getAttachedFiles().size());

			// by barcode
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("barcodes.barcodeData").matching("Barcode123"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename three", samples.get(0).getName());
			
			// by shared with
			samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("sharedWith").matching("group1"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("uniquename three", samples.get(0).getName());

			transaction.commit();

		} catch (Exception e) {
			if (transaction != null) {
				transaction.rollback();
			}
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}
	
	private Sample saveComplexSample(Sample sample3) {
		HibernateSandboxTest t = new HibernateSandboxTest();
		t.saveRadioAndChoiceDefinitions(dao, sample3.getSTemplate());
		dao.save(sample3.getSTemplate(), SampleTemplate.class);
		t.saveRadioAndChoiceDefinitions(dao, sample3);
		return saveSampleInContainer(sample3);
	}
	
	private Sample saveSampleInContainer(Sample sample) {
		Container container = sample.getSubSamples().get(0).getParentContainer();
		dao.update(container, Container.class);
		return dao.save(sample, Sample.class);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void searchSamplesAndSampleTemplates() throws InterruptedException {

		// save a sample and a sample template, both with searchable names
		Sample sample = TestFactory.createBasicSampleInContainer(testUser);
		sample.setName("polysearch sample");
		sample = saveSampleInContainer(sample);

		SampleTemplate template = rf.createSampleTemplate("polysearch template", testUser);
		template = dao.save(template, SampleTemplate.class);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			// build lucene index
			SearchSession searchSession = Search.session(sf.getCurrentSession());
			searchSession.massIndexer().startAndWait();

			// query targeting SampleEntity finds both the sample and the template
			List<SampleEntity> sampleEntities = searchSession.search(SampleEntity.class)
					.where(f -> f.match().field("name").matching("polysearch"))
					.fetchHits(20);
			assertNotNull(sampleEntities);
			assertEquals(2, sampleEntities.size());
			// fetchHits() returns an immutable list, so copy before sorting in place
			sampleEntities = new ArrayList<>(sampleEntities);
			Collections.sort(sampleEntities, (se1, se2) -> se1.getName().compareTo(se2.getName()));
			assertEquals("polysearch sample", sampleEntities.get(0).getName());
			assertInstanceOf(Sample.class, sampleEntities.get(0));
			assertEquals("polysearch template", sampleEntities.get(1).getName());
			assertInstanceOf(SampleTemplate.class, sampleEntities.get(1));

			// the same query targeting Sample finds only the sample
			List<Sample> samples = searchSession.search(Sample.class)
					.where(f -> f.match().field("name").matching("polysearch"))
					.fetchHits(20);
			assertNotNull(samples);
			assertEquals(1, samples.size());
			assertEquals("polysearch sample", samples.get(0).getName());

			// and targeting SampleTemplate finds only the template
			List<SampleTemplate> templates = searchSession.search(SampleTemplate.class)
					.where(f -> f.match().field("name").matching("polysearch"))
					.fetchHits(20);
			assertNotNull(templates);
			assertEquals(1, templates.size());
			assertEquals("polysearch template", templates.get(0).getName());

			transaction.commit();

		} catch (Exception e) {
			if (transaction != null) {
				transaction.rollback();
			}
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void searchSubSamples() throws InterruptedException {

		// save a sample with 3 subsamples
		Sample sample = TestFactory.createBasicSampleWithSubSamples(testUser, 3);
		SubSample subSample1 = sample.getSubSamples().get(0);
		subSample1.setName("test subSample #1");
		subSample1.setTags("tag1");
		SubSample subSample2 = sample.getSubSamples().get(1);
		String noteContent = "note1";
		subSample2.setName("test subSample #2");
		subSample2.addNote(noteContent, testUser);
		SubSample subSample3 = sample.getSubSamples().get(2);
		subSample3.setName("test subSample #3");
		ExtraTextField extraField = TestFactory.createExtraTextField("extraField", testUser, subSample3);
		final String testExtraFieldData = "extrafielddata";
		extraField.setData(testExtraFieldData);
		subSample3.addExtraField(extraField);
		subSample3.addBarcode(new Barcode("B1234", null));
		sample = saveSampleInContainer(sample);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			// build lucene index and query
			SearchSession searchSession = Search.session(sf.getCurrentSession());
			searchSession.massIndexer().startAndWait();

			// try various searches
			
			// search that doesn't match any sample
			List<SubSample> subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().fields("name", "name").matching("asdf"))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(0, subSamples.size());

			// find by name
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("subSample"))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(3, subSamples.size());
			// fetchHits() returns relevance-ordered hits, so sort by name before positional asserts
			subSamples = new ArrayList<>(subSamples);
			Collections.sort(subSamples, (ss1, ss2) -> ss1.getName().compareTo(ss2.getName()));
			assertEquals("test subSample #1", subSamples.get(0).getName());

			// by tag
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("tag1"))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(1, subSamples.size());
			assertEquals("test subSample #1", subSamples.get(0).getName());

			// by note content
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().field("notes.fieldData").matching(noteContent))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(1, subSamples.size());
			assertEquals("test subSample #2", subSamples.get(0).getName());

			// by extra field content
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().field("extraFields.fieldData").matching(testExtraFieldData))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(1, subSamples.size());
			assertEquals("test subSample #3", subSamples.get(0).getName());

			// by barcode
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().field("barcodes.barcodeData").matching("B1234"))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(1, subSamples.size());
			assertEquals("test subSample #3", subSamples.get(0).getName());			
			
			// by parent sample id 
			final Long sampleId = sample.getId();
			subSamples = searchSession.search(SubSample.class)
					.where(f -> f.match().field("parentSampleId").matching(sampleId))
					.fetchHits(20);
			assertNotNull(subSamples);
			assertEquals(3, subSamples.size());
			subSamples = new ArrayList<>(subSamples);
			Collections.sort(subSamples, (ss1, ss2) -> ss1.getName().compareTo(ss2.getName()));
			assertEquals("test subSample #1", subSamples.get(0).getName());

			transaction.commit();

		} catch (Exception e) {
			if (transaction != null) {
				transaction.rollback();
			}
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void searchContainers() throws InterruptedException {

		Container container = rf.createListContainer("test container", testUser);
		ExtraTextField extraField = TestFactory.createExtraTextField("extraField", testUser, container);
		extraField.setData("myData");
		container.addExtraField(extraField);
		Container subContainer1 = rf.createListContainer("test subcontainer #1", testUser);
		subContainer1.setTags("tag1");
		Container subContainer2 = rf.createListContainer("test subcontainer #2", testUser);
		subContainer2.setTags("tag2");
		subContainer2.addBarcode(new Barcode("B456", null));
		container.addToNewLocation(subContainer1);
		container.addToNewLocation(subContainer2);
		Container topContainer = dao.save(container, Container.class);
		dao.save(subContainer1, Container.class);
		dao.save(subContainer2, Container.class);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			// build lucene index and query
			SearchSession searchSession = Search.session(sf.getCurrentSession());
			searchSession.massIndexer().startAndWait();

			// try various searches
			
			// search that doesn't match any container
			List<Container> containers = searchSession.search(Container.class)
					.where(f -> f.match().fields("name", "name").matching("asdf"))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(0, containers.size());

			// find by name
			containers = searchSession.search(Container.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("subcontainer"))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(2, containers.size());
			containers = new ArrayList<>(containers);
			Collections.sort(containers, (c1, c2) -> c1.getName().compareTo(c2.getName()));
			assertEquals("test subcontainer #1", containers.get(0).getName());
			assertEquals("test subcontainer #2", containers.get(1).getName());

			// by tag
			containers = searchSession.search(Container.class)
					.where(f -> f.match().fields("name", "tags", "description").matching("tag2"))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(1, containers.size());
			assertEquals("test subcontainer #2", containers.get(0).getName());

			// by extra field content
			containers = searchSession.search(Container.class)
					.where(f -> f.match().field("extraFields.fieldData").matching("myData"))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(1, containers.size());
			assertEquals("test container", containers.get(0).getName());

			// by barcode
			containers = searchSession.search(Container.class)
					.where(f -> f.match().field("barcodes.barcodeData").matching("B456"))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(1, containers.size());
			assertEquals("test subcontainer #2", containers.get(0).getName());	
			
			// by parent id 
			final Long topContainerId = topContainer.getId();
			containers = searchSession.search(Container.class)
					.where(f -> f.match().field("parentId").matching(topContainerId))
					.fetchHits(20);
			assertNotNull(containers);
			assertEquals(2, containers.size());
			containers = new ArrayList<>(containers);
			Collections.sort(containers, (c1, c2) -> c1.getName().compareTo(c2.getName()));
			assertEquals("test subcontainer #1", containers.get(0).getName());
			assertEquals("test subcontainer #2", containers.get(1).getName());

			transaction.commit();

		} catch (Exception e) {
			if (transaction != null) {
				transaction.rollback();
			}
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void combinedInventorySearch() throws InterruptedException {

		// save a sample with 2 subsamples
		Sample sample = TestFactory.createBasicSampleWithSubSamples(testUser, 3);
		sample.setTags("tag11, tag12");
		SubSample subSample1 = sample.getSubSamples().get(0);
		subSample1.setName("test subSample #1");
		subSample1.setTags("tag11");
		subSample1.addBarcode(new Barcode("b11", null));
		SubSample subSample2 = sample.getSubSamples().get(1);
		String noteContent = "note1";
		subSample2.setName("test subSample #2");
		subSample2.addNote(noteContent, testUser);
		sample = saveSampleInContainer(sample);

		// and three containers
		Container container = rf.createListContainer("test container", testUser);
		Container subContainer1 = rf.createListContainer("test subcontainer #1", testUser);
		subContainer1.setTags("tag11");
		subContainer1.addBarcode(new Barcode("b11", null));
		Container subContainer2 = rf.createListContainer("test subcontainer #2", testUser);
		subContainer2.setTags("tag12");
		subContainer2.addBarcode(new Barcode("b12", null));
		container.addToNewLocation(subContainer1);
		container.addToNewLocation(subContainer2);
		dao.save(container, Container.class);
		dao.save(subContainer1, Container.class);
		dao.save(subContainer2, Container.class);
		
		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			// build lucene index and query
			SearchSession searchSession = Search.session(sf.getCurrentSession());
			searchSession.massIndexer().startAndWait();

			// try various searches
			// search that doesn't match any sample
			List<InventoryRecord> foundInvRecords = searchSession.search(InventoryRecord.class)
					.where(f -> f.match().fields("name", "name").matching("asdf"))
					.fetchHits(20);
			assertNotNull(foundInvRecords);
			assertEquals(0, foundInvRecords.size());

			// search by tag, should match a sample and a first subsample 
			foundInvRecords = searchSession.search(InventoryRecord.class)
					.where(f -> f.match().field("tags").matching("tag11"))
					.fetchHits(20);
			assertNotNull(foundInvRecords);
			assertEquals(3, foundInvRecords.size());
			// fetchHits() returns an immutable list, so copy before sorting in place
			foundInvRecords = new ArrayList<>(foundInvRecords);
			Collections.sort(foundInvRecords, (ir1, ir2) -> ir1.getName().compareTo(ir2.getName()));
			assertEquals("test sample", foundInvRecords.get(0).getName());
			assertEquals("test subSample #1", foundInvRecords.get(1).getName());
			assertEquals("test subcontainer #1", foundInvRecords.get(2).getName());

			// search by barcode, should match a subsample and a container.
			// barcodeData is a @KeywordField (exact match, case-sensitive), so query the stored case.
			foundInvRecords = searchSession.search(InventoryRecord.class)
					.where(f -> f.match().field("barcodes.barcodeData").matching("b11"))
					.fetchHits(20);
			assertNotNull(foundInvRecords);
			assertEquals(2, foundInvRecords.size());
			
			// search by owner, should match everything
			foundInvRecords = searchSession.search(InventoryRecord.class)
					.where(f -> f.match().field("owner.username").matching(testUser.getUsername()))
					.fetchHits(20);
			assertNotNull(foundInvRecords);
			assertEquals(8, foundInvRecords.size());

			transaction.commit();

		} catch (Exception e) {
			if (transaction != null) {
				transaction.rollback();
			}
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

}
