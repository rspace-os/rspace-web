package com.researchspace.model.test;

import com.researchspace.search.impl.RSpaceLuceneAnalysisConfigurer;
import java.util.Properties;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.service.ServiceRegistry;

public class HibernateUtils {
  private static SessionFactory sessionFactory;

  public static SessionFactory getSessionFactory(String databaseName, Class<?>... clazzes) {
    try {
      Configuration configuration = createConfiguration(databaseName);
      for (Class<?> clazz : clazzes) {
        configuration.addAnnotatedClass(clazz);
      }
      doBuild(configuration);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return sessionFactory;
  }

  private static void doBuild(Configuration configuration) {
    ServiceRegistry serviceRegistry =
        new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build();
    sessionFactory = configuration.buildSessionFactory(serviceRegistry);
  }

  private static Configuration createConfiguration(String dbName) {
    Configuration configuration = new Configuration();
    // Hibernate settings equivalent to hibernate.cfg.xml's properties
    Properties settings = new Properties();
    settings.put(Environment.DRIVER, "com.mysql.cj.jdbc.Driver");
    settings.put(Environment.URL, "jdbc:mysql://localhost:3306/" + dbName + "?useSSL=false");
    settings.put(Environment.USER, "rspacedbuser");
    settings.put(Environment.PASS, "rspacedbpwd");
    settings.put("hibernate.search.backend.type", "lucene");
    settings.put("hibernate.search.backend.directory.type", "local-filesystem");
    settings.put("hibernate.search.backend.directory.root", dbName);
    // Hibernate Search 7 bootstraps strictly: every analyzer and normalizer referenced by an
    // indexed entity must be defined or the SessionFactory build fails. Use the production
    // configurer so these tests fail when it stops matching the entity annotations.
    settings.put(
        "hibernate.search.backend.analysis.configurer",
        RSpaceLuceneAnalysisConfigurer.class.getName());

    settings.put(Environment.DIALECT, "org.hibernate.dialect.MySQLDialect");
    settings.put(Environment.SHOW_SQL, "true");
    settings.put(Environment.CURRENT_SESSION_CONTEXT_CLASS, "thread");
    settings.put(Environment.HBM2DDL_AUTO, "create-drop");
    configuration.setProperties(settings);
    return configuration;
  }
}
