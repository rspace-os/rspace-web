package com.researchspace.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.record.TestFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.junit.jupiter.api.Test;

/**
 * Tests audit tables. 
 */
class HibernateAuditTest extends HibernateTest {
	
	/*
	 * Utility method to get the AuditReader which provides access to Envers API.
	 */
	private AuditReader getAuditReader() {
		return AuditReaderFactory.get(sf.getCurrentSession());
	}
	
	private <T> List<AuditedEntity<T>> getRevisionsForObject(Class<T> cls, Long primaryKey) {
		AuditReader ar = getAuditReader();
		AuditQuery q = ar.createQuery().forRevisionsOfEntity(cls, false, false)
				.add(AuditEntity.id().eq(primaryKey));
		return processGenericResults(q.getResultList(), cls);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private <T> List<AuditedEntity<T>> processGenericResults(List results, Class<T> clazz) {
		List<AuditedEntity<T>> rc = new ArrayList<>();

		for (int i = 0; i < results.size(); i++) {
			Object[] row = (Object[]) results.get(i);
			T entity = (T) row[0];
			DefaultRevisionEntity dre = (DefaultRevisionEntity) row[1];

			RevisionType revType = (RevisionType) row[2];
			rc.add(new AuditedEntity<>(entity, dre.getId(), revType));
		}
		return rc;
	}

	@Test
	public void searchSampleHistory() {

		// save new sample
		Sample sample = TestFactory.createBasicSampleInContainer(testUser);
		sample.setName("uniquename one");
		sample.setTags("tag1, tag2, tag3");
		sample.setDescription("description1");
		saveSampleInContainer(sample);
		
		SubSample subSample = sample.getSubSamples().get(0);
		assertEquals("1 ml", subSample.getQuantityInfo().toPlainString());
		
		// update sample name
		sample.setName("updated name");
		sample = dao.update(sample, Sample.class);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			List<AuditedEntity<SubSample>> subSampleHistory = getRevisionsForObject(SubSample.class, subSample.getId());
			assertEquals(2, subSampleHistory.size());
			assertEquals("test sample", subSampleHistory.get(0).getEntity().getName());
			assertEquals("1 ml", subSampleHistory.get(0).getEntity().getQuantityInfo().toPlainString());
			assertEquals("test sample", subSampleHistory.get(1).getEntity().getName());

			List<AuditedEntity<Sample>> sampleHistory = getRevisionsForObject(Sample.class, sample.getId());
			assertEquals(2, sampleHistory.size());
			assertEquals("uniquename one", sampleHistory.get(0).getEntity().getName());
			assertEquals("updated name", sampleHistory.get(1).getEntity().getName());
					
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
	
	private void saveSampleInContainer(Sample sample) {
		dao.save(sample.getSubSamples().get(0).getParentContainer(), Container.class);
		sample = dao.save(sample, Sample.class);
	}

	@Test
	public void searchSampleTemplateHistory() {

		// save new sample template
		SampleTemplate template = rf.createSampleTemplate("template uniquename", testUser);
		template = dao.save(template, SampleTemplate.class);

		// update template name
		template.setName("updated template name");
		template = dao.update(template, SampleTemplate.class);

		Long templateId = template.getId();

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			List<AuditedEntity<SampleTemplate>> templateHistory = getRevisionsForObject(SampleTemplate.class, templateId);
			assertEquals(2, templateHistory.size());
			assertInstanceOf(SampleTemplate.class, templateHistory.get(0).getEntity());
			assertInstanceOf(SampleTemplate.class, templateHistory.get(1).getEntity());
			assertEquals("template uniquename", templateHistory.get(0).getEntity().getName());
			assertEquals("updated template name", templateHistory.get(1).getEntity().getName());

			// Envers filters revisions by discriminator: no Sample revisions exist for the template id
			List<AuditedEntity<Sample>> sampleHistory = getRevisionsForObject(Sample.class, templateId);
			assertEquals(0, sampleHistory.size());

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

	@Test
	public void searchContainerHistory() {

		// create container with subcontainer
		Container container = rf.createListContainer("test container", testUser);
		ExtraTextField extraField = TestFactory.createExtraTextField("extraField", testUser, container);
		extraField.setData("myData");
		container.addExtraField(extraField);
		Container subContainer1 = rf.createListContainer("test subcontainer #1", testUser);
		subContainer1.setTags("tag1");
		container.addToNewLocation(subContainer1);
		Container topContainer = dao.save(container, Container.class);
		dao.save(subContainer1, Container.class);

		// create and add another subcontainer
		Container subContainer2 = rf.createListContainer("test subcontainer #2", testUser);
		subContainer2.setTags("tag2");
		topContainer.addToNewLocation(subContainer2);
		dao.update(topContainer, Container.class);
		dao.save(subContainer2, Container.class);
		
		// edit 1st subcontainer name
		subContainer1.setName("updated subcontainer #1");
		dao.update(subContainer1, Container.class);
		
		// update top container name
		topContainer.setName("updated container");
		dao.update(topContainer, Container.class);

		// edit 2nd subcontainer name
		subContainer1.setName("updated subcontainer #2");
		dao.update(subContainer1, Container.class);

		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			List<AuditedEntity<Container>> containerHistory = getRevisionsForObject(Container.class, topContainer.getId());
			assertEquals(3, containerHistory.size());

			// 1st revision
			assertEquals("test container", containerHistory.get(0).getEntity().getName());
			assertEquals(1, containerHistory.get(0).getEntity().getContentCount());
			assertEquals(0, containerHistory.get(0).getEntity().getLocations().size()); // locations not stored
			// 2nd revision
			assertEquals("test container", containerHistory.get(1).getEntity().getName());
			assertEquals(2, containerHistory.get(1).getEntity().getContentCount());
			// 3rd revision
			assertEquals("updated container", containerHistory.get(2).getEntity().getName());
			assertEquals(2, containerHistory.get(2).getEntity().getContentCount());

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

	@Test
	public void retrieveHistoricalSubSampleForPublishedDoiIdentifier() {

		// save new sample
		Sample sample = TestFactory.createBasicSampleInContainer(testUser);
		sample.setName("uniquename one");
		saveSampleInContainer(sample);

		SubSample subSample = sample.getSubSamples().get(0);
		subSample.setName("test subsample");
		DigitalObjectIdentifier igsnIdentifier = rf.createDoiIdentifier("IGSN01");
		igsnIdentifier.setTitle("igsn 01");
		subSample.addIdentifier(igsnIdentifier);
		subSample = dao.update(subSample, SubSample.class);

		// update sample name
		sample.setName("updated uniquename one");
		sample = dao.update(sample, Sample.class);

		// update identifier
		igsnIdentifier.setTitle("updated igsn 01");
		igsnIdentifier = dao.update(igsnIdentifier, DigitalObjectIdentifier.class);
		
		// update subsample name
		subSample.setName("updated test subsample");
		subSample = dao.update(subSample, SubSample.class);
		
		Session session = null;
		Transaction transaction = null;
		try {
			session = sf.getCurrentSession();
			transaction = session.getTransaction();
			transaction.begin();

			List<AuditedEntity<DigitalObjectIdentifier>> identifierHistory = getRevisionsForObject(DigitalObjectIdentifier.class, igsnIdentifier.getId());
			assertEquals(4, identifierHistory.size());
			assertEquals("igsn 01", identifierHistory.get(0).getEntity().getTitle());
			assertEquals("test subsample", identifierHistory.get(0).getEntity().getInventoryRecord().getName());
			assertEquals("uniquename one", ((SubSample) identifierHistory.get(0).getEntity().getInventoryRecord()).getSample().getName());
			assertEquals("igsn 01", identifierHistory.get(1).getEntity().getTitle());
			assertEquals("test subsample", identifierHistory.get(1).getEntity().getInventoryRecord().getName());
			assertEquals("updated uniquename one", ((SubSample) identifierHistory.get(1).getEntity().getInventoryRecord()).getSample().getName());
			assertEquals("updated igsn 01", identifierHistory.get(2).getEntity().getTitle());
			assertEquals("test subsample", identifierHistory.get(2).getEntity().getInventoryRecord().getName());
			assertEquals("updated uniquename one", ((SubSample) identifierHistory.get(2).getEntity().getInventoryRecord()).getSample().getName());
			assertEquals("updated igsn 01", identifierHistory.get(3).getEntity().getTitle());
			assertEquals("updated test subsample", identifierHistory.get(3).getEntity().getInventoryRecord().getName());
			assertEquals("updated uniquename one", ((SubSample) identifierHistory.get(3).getEntity().getInventoryRecord()).getSample().getName());

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
