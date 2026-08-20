package com.researchspace.model.test;

import java.util.List;
import java.util.function.BiConsumer;

import java.util.function.Consumer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

public class TestDao {

	public <T> TestDao(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private SessionFactory sessionFactory;

	<T> T load(Long id, Class<T> clazz) {
		return load(id, clazz, null);
	}
	
	<T> T load(Long id, Class<T> clazz,  Consumer<T> initializer) {
		Transaction transaction = null;
		T rc = null;
		Session session = null;
		try {
			session = sessionFactory.openSession();
			// start a transaction
			transaction = session.beginTransaction();
			rc = session.get(clazz, id);
			
			if (initializer != null) {
				initializer.accept(rc);
			}
			
			// commit transaction
			transaction.commit();
			return rc;

		} catch (Exception e) {
			handleRollback(transaction, e);
		} finally {
			session.close();
		}
		return null;
	}

	<T> Long count(String tablename) {
		Transaction transaction = null;
		Long rc = 0L;
		Session session = null;
		try {
			session = sessionFactory.openSession();
			// start a transaction
			transaction = session.beginTransaction();
			// save the student object
			rc = session.createQuery("select count(id) from " + tablename, Long.class).getSingleResult();
			// commit transaction
			transaction.commit();
			return rc;

		} catch (Exception e) {
			handleRollback(transaction, e);
		} finally {
			session.close();
		}
		return null;
	}

	<T> T save(T item, Class<? extends T> clazz) {
		 doSaveOrUpdate(item, (item2,session)->session.save(item));
		 return item;
	}
	
	<T> T update(T item, Class<? extends T> clazz)  {
		 doSaveOrUpdate(item, (item2,session)->session.saveOrUpdate(item));
		 return item;
	}

	private <T> void doSaveOrUpdate(T item,  BiConsumer<T, Session> saver)  {
		Transaction transaction = null;
		Session session = null;
		try {
			session = sessionFactory.openSession();
			// start a transaction
			transaction = session.beginTransaction();
			// save the student object
			
			saver.accept(item, session);
			// commit transaction
			transaction.commit();
		//	return item;

		} catch (Exception e) {
			handleRollback(transaction, e);
			throw e;
		} finally {
			if (session != null) {
				session.close();
			}
		}
	}

	<T> List<T> getAll(Class<T> clazz, String tableName) {
		Transaction transaction = null;
		Session session = null;
		List<T> rc = null;
		try {
			session = sessionFactory.openSession();
			// start a transaction
			transaction = session.beginTransaction();
			// save the student object
			rc = session.createQuery("from " + tableName, clazz).list();
			// commit transaction
			transaction.commit();
			return rc;

		} catch (Exception e) {
			handleRollback(transaction, e);
		} finally {
			session.close();
		}
		return null;
	}

	private void handleRollback(Transaction transaction, Exception e) {
		if (transaction != null) {
			transaction.rollback();
		}
		e.printStackTrace();
	}

	 

}
