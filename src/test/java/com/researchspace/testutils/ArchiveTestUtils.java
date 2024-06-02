package com.researchspace.testutils;

import com.researchspace.Constants;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveModel;
import com.researchspace.archive.model.ArchiveUsers;
import com.researchspace.archive.model.ArchiveUsersTestData;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.XMLReadWriteUtils;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.UserPreference;
import com.researchspace.model.UserProfile;
import com.researchspace.model.preference.Preference;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Utility methods for manipulating archives in test code. */
public class ArchiveTestUtils {

  public static ArchiveUsersTestData createArchiveUsersTestData() {
    ArchiveUsersTestData rc = new ArchiveUsersTestData();
    User user = TestFactory.createAnyUserWithRole("fbloggs", Constants.PI_ROLE);
    User admin = TestFactory.createAnyUserWithRole("zbloggs", Constants.ADMIN_ROLE);
    Group g1 = TestFactory.createAnyGroup(user, new User[] {admin});
    // need to set these explicitly
    ArchiveUsers userGroupInfo = new ArchiveUsers();
    userGroupInfo.setUsers(TransformerUtils.toSet(user, admin));
    userGroupInfo.setGroups(TransformerUtils.toSet(g1));
    userGroupInfo.setUserGroups(g1.getUserGroups());
    UserPreference pref =
        new UserPreference(Preference.BROADCAST_REQUEST_BY_EMAIL, admin, Boolean.TRUE.toString());
    userGroupInfo.setUserPreferences(TransformerUtils.toSet(pref));

    Community community = new Community();
    community.addAdmin(admin);
    community.setUniqueName(CoreTestUtils.getRandomName(5));
    community.addLabGroup(g1);
    community.setDisplayName("display");
    community.setProfileText("info about community");
    userGroupInfo.setCommunities(TransformerUtils.toSet(community));

    UserProfile up = new UserProfile(admin);
    up.setExternalLinkDisplay("mylink");
    up.setExternalLinkURL("http://google.com");
    up.setProfileText("blah");
    userGroupInfo.setProfiles(TransformerUtils.toSet(up));
    // need to set these explicitly

    rc.setAdmin(admin);
    rc.setUser(user);
    rc.setGroup(g1);
    rc.setCommunity(community);
    rc.setPreferences(pref);
    rc.setProfile(up);
    rc.setArchiveUsers(userGroupInfo);
    return rc;
  }

  /**
   * Takes a transient {@link ArchiveUsers} object, writes to XML, and reads back again
   *
   * @param userGroupInfo
   * @return
   * @throws IOException
   * @throws Exception
   * @throws JAXBException
   */
  public static ArchiveUsers writeToXMLAndReadFromXML(ArchiveUsers userGroupInfo)
      throws IOException, Exception, JAXBException {
    File tmpFile = File.createTempFile("users", ".xml");
    XMLReadWriteUtils.toXML(tmpFile, userGroupInfo, ArchiveUsers.class);
    File schemaFile = File.createTempFile("usersschema", ".xsd");
    // generate schema so as to be able to unmarshal X-references
    XMLReadWriteUtils.generateSchemaFromXML(schemaFile, ArchiveUsers.class);
    ArchiveUsers fromXml = XMLReadWriteUtils.fromXML(tmpFile, ArchiveUsers.class, schemaFile, null);
    return fromXml;
  }

  public static <T> T writeToXMLAndReadFromXML(T data, Class<T> clazz)
      throws IOException, Exception, JAXBException {
    File tmpFile = File.createTempFile("users", ".xml");
    XMLReadWriteUtils.toXML(tmpFile, data, clazz);
    File schemaFile = File.createTempFile("usersschema", ".xsd");
    // generate schema so as to be able to unmarshal X-references
    XMLReadWriteUtils.generateSchemaFromXML(schemaFile, clazz);
    T fromXml = XMLReadWriteUtils.fromXML(tmpFile, clazz, schemaFile, null);
    return fromXml;
  }

  /**
   * Gets all html files in an archive, including subfolders
   *
   * @param zipFolder an expanded zip folder
   * @return
   */
  public static Collection<File> getAllHTMLFilesInArchive(File zipFolder) {
    return FileUtils.listFiles(
        zipFolder, FileFilterUtils.suffixFileFilter("html"), TrueFileFilter.INSTANCE);
  }

  /**
   * Gets all XML files in an archive, including subfolders
   *
   * @param zipFolder an expanded zip folder
   * @return
   */
  public static Collection<File> getAllXMLFilesInArchive(File zipFolder) {
    return FileUtils.listFiles(
        zipFolder, FileFilterUtils.suffixFileFilter("xml"), TrueFileFilter.INSTANCE);
  }

  /**
   * Gets all the files in an archive, including subfolders
   *
   * @param zipFolder an expanded zip folder
   * @return
   */
  public static Collection<File> getAllFilesInArchive(File zipFolder) {
    Collection<File> files =
        FileUtils.listFiles(zipFolder, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
    return files;
  }

  public static long getTopLevelMediaFileCount(File zipFolder) {
    Collection<File> files = getAllFilesInArchive(zipFolder);
    return files.stream().filter(f -> f.getName().startsWith("media_")).count();
  }

  public static ArchiveManifest getManifest(File zipFolder) throws IOException {
    File manifest =
        FileUtils.listFiles(
                zipFolder,
                FileFilterUtils.nameFileFilter(ExportImport.EXPORT_MANIFEST),
                TrueFileFilter.INSTANCE)
            .iterator()
            .next();
    ArchiveModel am = new ArchiveModel();
    am.setManifestFile(manifest);
    return am.getManifest();
  }

  /**
   * Compares 2 objects for equality, where equality is based on the value of XMLElement-annotated
   * getXXX/isXXX methods. This method recursively introspects collections via reflection.
   *
   * @param original
   * @param fromXML
   * @return
   * @throws IllegalArgumentException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   */
  public static <T> boolean areEquals(T original, T fromXML)
      throws IllegalArgumentException, IllegalAccessException, InvocationTargetException {
    Class clazz = original.getClass();
    for (Method m : clazz.getMethods()) {
      Annotation ann = m.getAnnotation(XmlElement.class);
      // if element is null, try attribute
      if (ann == null) {
        ann = m.getAnnotation(XmlAttribute.class);
      }
      if (ann != null) {
        if (m.getGenericParameterTypes().length == 0 && m.getName().startsWith("is")
            || m.getName().startsWith("get")) {
          Object oVal = m.invoke(original);
          Object from = m.invoke(fromXML);
          // if both are null, this is possibly OK.
          if (oVal == null && from == null) {
            continue;
          }
          // fail if one is null but not the other
          if (oVal == null && from != null || oVal != null && from == null) {
            return false;
          }
          if (oVal.equals(from)) {
            continue; // ok
            // else see if it's an association to another element
          } else if (isXMLTypeAnnotatedClass(oVal)) {
            return areEquals(oVal, fromXML);
            // else go through collections of elements
          } else if (oVal instanceof Collection) {
            Collection ocoll = (Collection) oVal;
            Collection icoll = (Collection) from;
            if (ocoll.size() > 0) {
              Iterator oit = ocoll.iterator();
              Iterator iit = ocoll.iterator();
              while (oit.hasNext()) {
                Object oItem1 = oit.next();
                Object iItem2 = iit.next();
                if (isXMLTypeAnnotatedClass(oItem1)) {
                  return areEquals(oItem1, iItem2);
                }
              }
            }
          } else {
            return false;
          }
        }
      }
    }
    return true;
  }

  private static boolean isXMLTypeAnnotatedClass(Object oItem1) {
    return oItem1.getClass().getAnnotation(XmlType.class) != null
        || oItem1.getClass().getAnnotation(XmlRootElement.class) != null;
  }

  /**
   * Boolean test for whether the expanded archive contains a file with the given fileName.
   *
   * @param zipFolder
   * @param filename
   * @return
   */
  public static boolean archiveContainsFile(File zipFolder, String filename) {
    Collection<File> files =
        FileUtils.listFiles(
            zipFolder, FileFilterUtils.nameFileFilter(filename), TrueFileFilter.INSTANCE);
    return files.size() > 0;
  }

  /**
   * Boolean test that the file of given name is not empty, i.e has a non-zero length
   *
   * @param zipFolder
   * @param filename
   * @return
   */
  public static boolean archiveFileHasContent(File zipFolder, String filename) {
    Collection<File> files =
        FileUtils.listFiles(
            zipFolder, FileFilterUtils.nameFileFilter(filename), TrueFileFilter.INSTANCE);
    return files.iterator().next().length() > 0;
  }

  /**
   * @param zipFolder Expanded zip folder of HTML export
   * @param uniqueFileName A filename to select 1 file
   * @param htmlSelector A JSoup selector expression to identify an element in the file
   * @param predicate A boolean test on the located Element.
   * @return <code>true</code> if predicate matches.
   */
  public static boolean assertPredicateOnHtmlFile(
      File zipFolder, String uniqueFileName, String htmlSelector, Predicate<Element> predicate) {

    Collection<File> htmlFiles = ArchiveTestUtils.getAllHTMLFilesInArchive(zipFolder);

    long count =
        htmlFiles.stream()
            .filter(f -> f.getName().contains(uniqueFileName))
            .filter(
                f -> {
                  try {
                    String htmlFile = FileUtils.readFileToString(f, "UTF-8");
                    Element doc = Jsoup.parse(htmlFile);
                    Element absLink = doc.select(htmlSelector).first();
                    boolean b = predicate.test(absLink);
                    return b;
                  } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                  }
                })
            .count();
    return count > 0;
  }
}
