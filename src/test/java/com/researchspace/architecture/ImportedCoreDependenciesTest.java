package com.researchspace.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the layering that the repository split used to enforce at compile time: the sources
 * imported from the absorbed rspace-core-util, rspace-audit, and rspace-core-model repositories
 * must not depend on rspace-web-owned code, so they stay conceptually "below" the web application
 * (and could be split out again if that were ever needed).
 *
 * <p>The imported surface is selected explicitly. Most imported packages contain no
 * rspace-web-owned classes and are matched exactly (not by subpackage: for example
 * com.researchspace.model is imported, while its dtos/frontend subpackages are web-owned). Three
 * packages are shared between imported and web-owned classes, so their imported classes are listed
 * individually; the pre-existing web classes in them (for example AuditTrailHandler and
 * AuditTrailHandlerImpl) are deliberately not selected.
 *
 * <p>Only classes compiled into target/classes are considered dependency targets, so dependencies
 * on jar-provided packages (the JDK, third-party libraries, and rspace-os libraries such as
 * rspace-document-conversion-spi) are always allowed.
 */
class ImportedCoreDependenciesTest {

  /** Packages imported wholly from the absorbed repositories, matched exactly. */
  private static final Set<String> IMPORTED_PACKAGES =
      Set.of(
          "com.researchspace",
          "com.researchspace.b2inst.model.common",
          "com.researchspace.b2inst.model.metadata",
          "com.researchspace.b2inst.model.request",
          "com.researchspace.b2inst.model.response",
          "com.researchspace.core.util.cache",
          "com.researchspace.core.util.imageutils",
          "com.researchspace.core.util.jsonserialisers",
          "com.researchspace.core.util.progress",
          "com.researchspace.core.util.structs",
          "com.researchspace.core.util.throttling",
          "com.researchspace.core.util.version",
          "com.researchspace.maintenance.model",
          "com.researchspace.model",
          "com.researchspace.model.apps",
          "com.researchspace.model.audit",
          "com.researchspace.model.audittrail",
          "com.researchspace.model.comms",
          "com.researchspace.model.comms.data",
          "com.researchspace.model.core",
          "com.researchspace.model.dmps",
          "com.researchspace.model.dto",
          "com.researchspace.model.elninventory",
          "com.researchspace.model.externalWorkflows",
          "com.researchspace.model.field",
          "com.researchspace.model.inventory",
          "com.researchspace.model.inventory.field",
          "com.researchspace.model.netfiles",
          "com.researchspace.model.oauth",
          "com.researchspace.model.permissions",
          "com.researchspace.model.preference",
          "com.researchspace.model.raid",
          "com.researchspace.model.record",
          "com.researchspace.model.stoichiometry",
          "com.researchspace.model.system",
          "com.researchspace.model.units",
          "com.researchspace.model.utils",
          "com.researchspace.model.views",
          "com.researchspace.model.views.search",
          "com.researchspace.session");

  /**
   * Imported classes in the three packages shared with pre-existing rspace-web classes
   * (com.researchspace.core.util, com.researchspace.model.events,
   * com.researchspace.service.audit.search).
   */
  private static final Set<String> IMPORTED_CLASSES =
      Set.of(
          "com.researchspace.core.util.ASearchResultEntry",
          "com.researchspace.core.util.AbstractURLPaginator",
          "com.researchspace.core.util.BasicPaginationCriteria",
          "com.researchspace.core.util.BasicSearchResultEntry",
          "com.researchspace.core.util.CollectionFilter",
          "com.researchspace.core.util.CommandLineRunner",
          "com.researchspace.core.util.DateRange",
          "com.researchspace.core.util.DateRangeAdjustable",
          "com.researchspace.core.util.DateRangeRestrictor",
          "com.researchspace.core.util.DateUtil",
          "com.researchspace.core.util.DefaultTimeSource",
          "com.researchspace.core.util.DefaultURLPaginator",
          "com.researchspace.core.util.EscapeReplacement",
          "com.researchspace.core.util.FieldParserConstants",
          "com.researchspace.core.util.FileOperator",
          "com.researchspace.core.util.FilterCriteria",
          "com.researchspace.core.util.FolderOperator",
          "com.researchspace.core.util.IDescribable",
          "com.researchspace.core.util.IPagination",
          "com.researchspace.core.util.ISearchResults",
          "com.researchspace.core.util.JacksonUtil",
          "com.researchspace.core.util.LimitedBytesFromURLRetriever",
          "com.researchspace.core.util.LinkUtils",
          "com.researchspace.core.util.MediaUtils",
          "com.researchspace.core.util.NullCache",
          "com.researchspace.core.util.NumberUtils",
          "com.researchspace.core.util.ObjectToStringPropertyTransformer",
          "com.researchspace.core.util.PaginationObject",
          "com.researchspace.core.util.PaginationUtil",
          "com.researchspace.core.util.RSCollectionUtils",
          "com.researchspace.core.util.RequestUtil",
          "com.researchspace.core.util.ResponseUtil",
          "com.researchspace.core.util.SearchResultEntry",
          "com.researchspace.core.util.SearchResultsImpl",
          "com.researchspace.core.util.SecureStringUtils",
          "com.researchspace.core.util.SortOrder",
          "com.researchspace.core.util.StringGenerator",
          "com.researchspace.core.util.TimeSource",
          "com.researchspace.core.util.Transformer",
          "com.researchspace.core.util.TransformerUtils",
          "com.researchspace.core.util.UISearchTerm",
          "com.researchspace.core.util.URLGenerator",
          "com.researchspace.core.util.XMLReadWriteUtils",
          "com.researchspace.core.util.ZipUtils",
          "com.researchspace.service.audit.search.AbstractAuditSrchConfigValidator",
          "com.researchspace.service.audit.search.AuditTrailSearchElement",
          "com.researchspace.service.audit.search.AuditTrailSearchResult",
          "com.researchspace.service.audit.search.AuditTrailUISearchConfig",
          "com.researchspace.service.audit.search.BasicLogQuerySearcher",
          "com.researchspace.service.audit.search.IAuditFileSearch",
          "com.researchspace.service.audit.search.IAuditTrailSearch",
          "com.researchspace.service.audit.search.IAuditTrailSearchConfig",
          "com.researchspace.service.audit.search.ILogResourceTracker",
          "com.researchspace.service.audit.search.LogFileTracker",
          "com.researchspace.service.audit.search.LogLine",
          "com.researchspace.service.audit.search.LogLineContentProvider",
          "com.researchspace.service.audit.search.LogLineContentProviderImpl",
          "com.researchspace.service.audit.search.LogLineParser",
          "com.researchspace.model.events.AccountEventType",
          "com.researchspace.model.events.GroupEventType",
          "com.researchspace.model.events.GroupMembershipEvent",
          "com.researchspace.model.events.UserAccountEvent");

  private static JavaClasses productionClasses;
  private static Set<String> productionClassNames;

  @BeforeAll
  static void importProductionClasses() {
    productionClasses = new ClassFileImporter().importPath(Paths.get("target/classes"));
    productionClassNames =
        productionClasses.stream()
            .map(ImportedCoreDependenciesTest::outermostName)
            .collect(Collectors.toSet());
  }

  /** Nested classes count as part of their outermost class. */
  private static String outermostName(JavaClass clazz) {
    String name = clazz.getFullName();
    int nested = name.indexOf('$');
    return nested == -1 ? name : name.substring(0, nested);
  }

  private static boolean isImported(JavaClass clazz) {
    return IMPORTED_PACKAGES.contains(clazz.getPackageName())
        || IMPORTED_CLASSES.contains(outermostName(clazz));
  }

  @Test
  void importedSelectionIsNotVacuous() {
    long selected =
        productionClasses.stream().filter(ImportedCoreDependenciesTest::isImported).count();
    // ~690 top-level classes were imported; guard against the rule silently
    // selecting nothing if the class output path or package layout changes
    org.junit.jupiter.api.Assertions.assertTrue(
        selected > 500,
        "expected the imported-class selection to match hundreds of classes, got " + selected);
  }

  @Test
  void importedCoreClassesDoNotDependOnWebOwnedClasses() {
    DescribedPredicate<JavaClass> imported =
        DescribedPredicate.describe(
            "imported from the absorbed core repositories",
            ImportedCoreDependenciesTest::isImported);
    DescribedPredicate<JavaClass> webOwned =
        DescribedPredicate.describe(
            "owned by rspace-web",
            target -> productionClassNames.contains(outermostName(target)) && !isImported(target));

    noClasses()
        .that(imported)
        .should()
        .dependOnClassesThat(webOwned)
        .because(
            "the absorbed core-util/audit/core-model sources sit below the web application, as"
                + " the old repository split enforced")
        .check(productionClasses);
  }
}
