package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.customliquibaseupdates.CreateDefaultInstrumentTemplate_RSDEV1219;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.PidinstAlreadyLinkedException;
import com.researchspace.service.inventory.PidinstLookupManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** See {@link PidinstLookupManager}. */
@Slf4j
@Service("pidinstLookupManager")
public class PidinstLookupManagerImpl implements PidinstLookupManager {

  /** The only DataCite state a lookup offers: a publicly resolvable DOI. */
  static final String STATE_FINDABLE = "findable";

  /** A DOI, bare or behind a doi.org resolver; group 1 is the bare DOI. */
  static final Pattern DOI_QUERY =
      Pattern.compile(
          "^(?:https?://(?:dx\\.)?doi\\.org/)?(10\\.\\d{4,9}/\\S+)$", Pattern.CASE_INSENSITIVE);

  /**
   * A query that may be pasted into DataCite's {@code doi:*...*} wildcard. DataCite's {@code query}
   * is Elasticsearch query-string syntax, so this is an allow-list of the characters that leave the
   * wildcard a single term: it excludes whitespace and every character that would close the clause
   * or start a new one ({@code " * ? : ( ) [ ] { } ^ ~ \ + = ! < > &} and {@code |}), so what the
   * user typed cannot turn the retry into a different query.
   *
   * <p>It admits {@code /} and {@code -}, which <em>are</em> reserved in query-string syntax, on
   * the evidence that DataCite accepts them inside a wildcard term rather than on the syntax alone:
   * verified 2026-09-17 against api.datacite.org, where {@code doi:*qvtb/aw74*} answers 200 and
   * {@code doi:*5281/zenodo*} (12.89M) narrows {@code doi:*5281*} (12.91M), so the wildcard really
   * does span the slash, while {@code doi:*"broken*} answers 400. Keeping {@code /} is what lets a
   * pasted prefix/suffix pair match; widening this class further needs the same kind of evidence.
   */
  static final Pattern DOI_FRAGMENT = Pattern.compile("^[A-Za-z0-9._/-]+$");

  /**
   * The characters DataCite's {@code query} reserves, escaped before a free-text search so the
   * user's words are matched rather than parsed. Left unescaped they answer 400, not an empty page,
   * and the dialog can only show that as an error: verified 2026-09-18 against api.datacite.org,
   * where {@code foo"bar}, {@code foo[bar}, {@code foo{bar}, {@code (foo}, {@code foo!},
   * {@code foo^} and {@code zeiss &&} all answer 400 while every escaped form answers 200.
   *
   * <p>{@code /} is deliberately absent although query-string syntax reserves it: DataCite answers
   * 400 for {@code 10.5281\/zenodo} and 200 for {@code 10.5281/zenodo}, so escaping it would break
   * the pasted DOI fragments this search exists to match. Escaping costs nothing where it is not
   * needed, checked the same day: {@code \Zeiss} and {@code Zeiss} both answer 71,
   * {@code spectrometer\*} and {@code spectrometer} both 146.
   *
   * <p>{@code <}, {@code >} and {@code =} are absent too, and cannot be added: a backslash before
   * one is ignored, so escaping them is not available even in principle. They need no handling,
   * because a range only exists attached to a field and {@code :} is escaped here, which is what
   * builds one. Measured 2026-09-18: {@code publicationYear:>2020} answers 95,411,191 but
   * {@code publicationYear\:>2020}, which is what this sends, answers 15, the same as the plain
   * {@code publicationYear 2020}. Loose, {@code Zeiss>4} answers 6,539, which is exactly what
   * {@code Zeiss 4} answers and what every separator the analyser splits on answers, so the
   * character is inert rather than parsed. Comparing it against {@code Zeiss} alone (42,170) only
   * measures the second term.
   */
  private static final Pattern DATACITE_RESERVED =
      Pattern.compile("([\\\\+\\-&|!(){}\\[\\]^\"~*?:])");

  /**
   * The bare boolean operators, which no character class catches. A dangling one is a parse error
   * in its own right ({@code abc OR} and {@code NOT} at the end both answer 400), and escaping the
   * first letter is what stops the parser reading the word as an operator.
   */
  private static final Pattern DATACITE_OPERATOR = Pattern.compile("\\b(AND|OR|NOT)\\b");

  /**
   * The query-string syntax B2INST (InvenioRDM, Elasticsearch) would parse inside a wildcard term,
   * escaped by {@link #containsForB2inst(String)}. Unlike DataCite's set it includes {@code /},
   * which B2INST answers 400 for unescaped and accepts escaped; it leaves out {@code *} and {@code
   * ?}, which stay wildcards, and {@link #B2INST_REMOVED}.
   */
  private static final Pattern B2INST_RESERVED = Pattern.compile("([\\\\+\\-=&|!(){}\\[\\]^~:/])");

  /**
   * Removed from a B2INST query rather than escaped: a quote because the whole query is already one
   * phrase, and {@code <} and {@code >} because Elasticsearch cannot escape them at all
   * (https://www.elastic.co/docs/reference/query-languages/query-dsl/query-dsl-query-string-query#reserved-characters).
   */
  private static final Pattern B2INST_REMOVED = Pattern.compile("[\"<>]");

  /**
   * What Lucene's classic query parser, and so B2INST, splits a query on. Java's {@code \s} misses
   * U+3000, which the parser lists as whitespace (QueryParser.jj {@code _WHITESPACE}), so an
   * unescaped one split the query back into clauses: {@code *Instr1<U+3000>OR<U+3000>**} matched
   * all 810 records on b2inst-test.gwdg.de (2026-09-25).
   */
  private static final Pattern LUCENE_WHITESPACE = Pattern.compile("[\\s\\u3000]+");

  /** A Handle under an ePIC prefix (B2INST mints 21.xxx), bare or behind hdl.handle.net. */
  static final Pattern HANDLE_QUERY =
      Pattern.compile(
          "^(?:https?://hdl\\.handle\\.net/)?(21\\.[A-Za-z0-9.]+/\\S+)$", Pattern.CASE_INSENSITIVE);

  @Autowired private B2instConnector b2instConnector;
  @Autowired private DataCiteConnector dataCiteConnector;
  @Autowired private DigitalObjectIdentifierDao doiDao;
  @Autowired private InstrumentTemplateDao instrumentTemplateDao;
  @Autowired private InstrumentEntityApiManager instrumentApiMgr;
  @Autowired private InventoryIdentifierApiManager identifierMgr;
  @Autowired private MessageSourceUtils messages;
  @Autowired private InventoryPermissionUtils invPermissions;

  @Override
  public ApiPidinstSearchResult search(String query, User user) {
    // strip, not trim: trim leaves U+3000 and other Unicode spaces, so padding passed the minimum
    String q = StringUtils.stripToEmpty(query);
    if (q.length() < MIN_QUERY_LENGTH) {
      throw new ApiRuntimeException(
          "errors.inventory.identifier.pidinstQueryTooShort", MIN_QUERY_LENGTH);
    }
    IdentifierType provider = enabledProvider();
    ApiPidinstSearchResult result = new ApiPidinstSearchResult();
    result.setProvider(provider.name());
    if (isPidOfTheOtherRegistry(q, provider)) {
      // decision 5: a DOI on a B2INST deployment (or a Handle on DataCite) cannot be resolved here
      return result;
    }
    Optional<String> pid = pidOf(q, provider);
    if (pid.isPresent()) {
      fetchByPid(pid.get(), provider).ifPresent(result.getHits()::add);
      result.setTotal(result.getHits().size());
    } else if (provider == IdentifierType.PIDINST_B2INST) {
      searchB2inst(q, result);
    } else {
      DataCiteDoiSearchResult hits = searchDataCite(q);
      // re-checked here as well as asked for in the request, so the rule holds whatever the index
      // returns (ADR 0009), and so this path cannot offer what fetchByPid would refuse
      hits.getData().stream()
          .filter(PidinstLookupManagerImpl::isInstrumentDoi)
          .filter(PidinstLookupManagerImpl::isFindable)
          .map(PidinstRecordMapper::fromDataCite)
          .filter(record -> record.getPid() != null)
          .forEach(result.getHits()::add);
      result.setTotal(hits.getMeta().getTotal());
    }
    result
        .getHits()
        .sort(
            Comparator.comparing(
                hit -> StringUtils.defaultString(hit.getName()).toLowerCase(Locale.ROOT)));
    annotateLinkedInstruments(result.getHits(), provider, user);
    return result;
  }

  @Override
  public ApiInstrument importInstrument(
      String pid, ApiTargetLocation newTargetLocation, User user) {
    IdentifierType provider = enabledProvider();
    ApiPidinstRecord record =
        pidOf(pid.trim(), provider)
            .flatMap(bare -> fetchByPid(bare, provider))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        messages.getMessage(
                            "errors.inventory.identifier.pidinstNotFound", new Object[] {pid})));
    linkedInstrumentOf(record, provider)
        .ifPresent(
            identifier -> {
              throw alreadyLinked(identifier, user);
            });
    InstrumentTemplate template =
        instrumentTemplateDao
            .findLockedTemplateByName(CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The locked default instrument template '"
                            + CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME
                            + "' is missing: the RSDEV-1219 Liquibase seeder has not run on this"
                            + " database"));
    ApiInstrument toCreate = PidinstRecordMapper.toApiInstrument(record, template);
    applyTargetLocation(toCreate, newTargetLocation);
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(toCreate, user);
    ApiInventoryDOI link = PidinstRecordMapper.toLinkedIdentifier(record, user);
    ApiInventoryRecordInfo linked =
        identifierMgr.linkExternalIdentifier(
            new GlobalIdentifier(created.getGlobalId()), link, user);
    log.info("Imported {} from {} as {}", record.getPid(), provider, created.getGlobalId());
    return (ApiInstrument) linked;
  }

  /**
   * The B2INST half of a free-text search: one query against the PUBLISHED index.
   *
   * <p>The account's own records under {@code /api/user/records} are deliberately not searched.
   * Only a public PID may be linked (RSDEV-1326), so a record still in draft, submitted or declined
   * is not a candidate, and the published index is exactly the set that is. Hits are filtered on
   * {@code is_published} as well, so the rule holds in RSpace whatever the index returns, and
   * {@code total} stays the provider's own.
   */
  private void searchB2inst(String query, ApiPidinstSearchResult result) {
    // removed, not replaced by a space, which would cut a word in two: Instr"1 must find Instr1
    String searchable = B2INST_REMOVED.matcher(query).replaceAll("").strip();
    // the minimum again, on what is left to match: <<<a would otherwise go out as *a*, which
    // matched all 810 records on b2inst-test.gwdg.de, and typed wildcards match nothing specific
    if (searchable.replaceAll("[*?]", "").length() < MIN_QUERY_LENGTH) {
      result.setTotal(0);
      return;
    }
    B2instSearchResult page =
        b2instConnector.searchRecords(containsForB2inst(searchable), MAX_HITS);
    page.getHits().getHits().stream()
        .filter(record -> Boolean.TRUE.equals(record.getIsPublished()))
        .map(PidinstRecordMapper::fromB2inst)
        .filter(record -> record.getPid() != null)
        .forEach(result.getHits()::add);
    Integer total = page.getHits().getTotal();
    result.setTotal(total == null ? result.getHits().size() : total);
  }

  /**
   * The query reaches DataCite as the user typed it, escaped but not wildcarded, so a search here
   * returns what the same words return in DataCite's own portal (RSDEV-1522, ADR 0009 decision 8).
   * The escape only keeps query-string syntax from reaching the parser, which would answer 400
   * rather than an empty page; it does not change which records match.
   *
   * <p>DataCite indexes the DOI as a keyword, so free text never matches a suffix or part of one
   * and a user who pasted half a DOI gets nothing. A {@code doi:*...*} wildcard does match, so an
   * empty first page is retried that way (ADR 0009 decision 7). The retry carries the raw query
   * rather than the escaped one, because it composes its own clause and {@link #DOI_FRAGMENT}
   * already limits what may go in it; its gate reads the raw query too, so escaping cannot change
   * which searches retry.
   */
  private DataCiteDoiSearchResult searchDataCite(String query) {
    DataCiteDoiSearchResult hits =
        dataCiteConnector.searchInstrumentDois(
            escapeForDataCite(query), MAX_HITS, InventorySettingType.PIDINST);
    if (!hits.getData().isEmpty() || !DOI_FRAGMENT.matcher(query).matches()) {
      return hits;
    }
    return dataCiteConnector.searchInstrumentDois(
        "doi:*" + query + "*", MAX_HITS, InventorySettingType.PIDINST);
  }

  /**
   * The B2INST query: the whole of what the user typed as one wildcard term, with its spaces
   * escaped so Elasticsearch does not split it (RSDEV-1522).
   *
   * <p>This is a real "contains", spaces included. Verified 2026-09-22 against b2inst-test.gwdg.de,
   * where {@code *nstr1\ prova_CO*} finds {@code Instr1 prova_COPY} - both words cut at both ends
   * and the match spanning the space - while {@code *Instr1\ prova_COPY*} finds only that record
   * where the unescaped {@code *Instr1 prova_COPY*} also returned {@code Instr1 prova 123}. Without
   * the escape Elasticsearch parses the query before the wildcards apply and splits it on
   * whitespace itself, leaving {@code *Instr1} (ends-with) and {@code prova_COPY*} (starts-with),
   * which B2INST then ORs.
   *
   * <p>One leading wildcard however many words the query holds, so it stays fast: 0.2-0.4s.
   *
   * <p>The user's query syntax is neutralised first, because the wildcards turn it into operators:
   * {@code *"Instr1\ prova_COPY"*} is {@code *} OR a phrase OR {@code *} and matched all 810
   * records on b2inst-test.gwdg.de where the quoted phrase alone matched 1, and {@code (}, {@code
   * ~} and {@code ^} did the same (2026-09-25). Quotes are dropped, since the whole query is
   * already one phrase and {@code *Instr1\ prova_COPY*} answers that same 1, and so are {@code <}
   * and {@code >}, which cannot be escaped; the rest of {@link #B2INST_RESERVED} is escaped to a
   * literal, which also turns the 400 on a pasted partial Handle such as {@code T11975/97g70-tsv60}
   * into its one record. {@code *} and {@code ?} stay live.
   */
  private static String containsForB2inst(String searchable) {
    String literal = B2INST_RESERVED.matcher(searchable).replaceAll("\\\\$1");
    return "*" + LUCENE_WHITESPACE.matcher(literal).replaceAll("\\\\ ") + "*";
  }

  /**
   * What the user typed, as a literal term for DataCite's Elasticsearch {@code query}. Only the
   * free-text call needs this: the wildcard retry composes its own clause and is already guarded by
   * {@link #DOI_FRAGMENT}, which admits nothing that could close it.
   */
  private static String escapeForDataCite(String query) {
    String escaped = DATACITE_RESERVED.matcher(query).replaceAll("\\\\$1");
    // after the character pass, so the backslash it inserts is not escaped again
    return DATACITE_OPERATOR.matcher(escaped).replaceAll("\\\\$1");
  }

  private IdentifierType enabledProvider() {
    if (b2instConnector.isConfiguredAndEnabled()) {
      return IdentifierType.PIDINST_B2INST;
    }
    if (dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST)) {
      return IdentifierType.PIDINST_DATACITE;
    }
    // the controllers' availability gate answers this first; kept so the manager is safe to call
    // directly, and phrased with the same key the gate uses
    throw new UnsupportedOperationException(
        messages.getMessage(
            "errors.inventory.identifier.integrationNotEnabled", new Object[] {"PIDINST"}));
  }

  /** The bare PID when the query has the enabled provider's PID shape; empty means free text. */
  private static Optional<String> pidOf(String query, IdentifierType provider) {
    Matcher matcher = pidPattern(provider).matcher(query);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static boolean isPidOfTheOtherRegistry(String query, IdentifierType provider) {
    IdentifierType other =
        provider == IdentifierType.PIDINST_B2INST
            ? IdentifierType.PIDINST_DATACITE
            : IdentifierType.PIDINST_B2INST;
    return pidPattern(other).matcher(query).matches();
  }

  private static Pattern pidPattern(IdentifierType provider) {
    return provider == IdentifierType.PIDINST_B2INST ? HANDLE_QUERY : DOI_QUERY;
  }

  private Optional<ApiPidinstRecord> fetchByPid(String pid, IdentifierType provider) {
    if (provider == IdentifierType.PIDINST_B2INST) {
      return b2instConnector
          .getRecordByHandle(pid)
          .filter(record -> Boolean.TRUE.equals(record.getIsPublished()))
          .map(PidinstRecordMapper::fromB2inst)
          .filter(record -> record.getPid() != null)
          // getRecordByHandle resolves the suffix alone, so any well-formed prefix reaches the same
          // record: 21.FAKE/abc would otherwise answer with the real 21.T11998/abc. Handles are
          // case-insensitive by spec, so the comparison is too.
          .filter(record -> pid.equalsIgnoreCase(record.getPid()));
    }
    return dataCiteConnector
        .findDoi(pid, InventorySettingType.PIDINST)
        .filter(PidinstLookupManagerImpl::isInstrumentDoi)
        .filter(PidinstLookupManagerImpl::isFindable)
        .map(PidinstRecordMapper::fromDataCite)
        .filter(record -> record.getPid() != null);
  }

  /**
   * Whether the DOI resolves publicly. Only a findable DOI may be linked (RSDEV-1326): a {@code
   * draft} or {@code registered} DOI has no public landing page, so linking one would put an
   * address in the Identifiers card that answers nothing. A DOI of another repository that is not
   * findable never reaches here, because DataCite answers 404 for it.
   */
  private static boolean isFindable(DataCiteDoi doi) {
    return doi.getAttributes() != null
        && STATE_FINDABLE.equalsIgnoreCase(doi.getAttributes().getState());
  }

  private static boolean isInstrumentDoi(DataCiteDoi doi) {
    return doi.getAttributes() != null
        && doi.getAttributes().getTypes() != null
        && PidinstRecordMapper.RESOURCE_TYPE_INSTRUMENT.equalsIgnoreCase(
            doi.getAttributes().getTypes().getResourceTypeGeneral());
  }

  /**
   * Stamps every hit whose PID an instrument in this deployment already links. The link rows are
   * one query for the whole page rather than one per hit, and none of them is cached with the
   * provider page because link status is local and changes independently of it. Visibility is then
   * one permission check per <em>linked</em> hit, bounded by {@link PidinstLookupManager#MAX_HITS},
   * which for a caller who cannot plainly read the holder reaches the list-of-materials query
   * inside limited read.
   *
   * <p>Every such hit is marked {@code alreadyLinked}, so Import can be refused with a reason, but
   * names the instrument only when {@code user} may read it: the registry record is public, an
   * RSpace instrument the caller may not read is not the search's to name (RSDEV-1505).
   */
  private void annotateLinkedInstruments(
      List<ApiPidinstRecord> hits, IdentifierType provider, User user) {
    List<String> stored =
        hits.stream().flatMap(PidinstLookupManagerImpl::storedValuesOf).distinct().toList();
    Map<String, DigitalObjectIdentifier> linkedByStoredValue = new HashMap<>();
    for (DigitalObjectIdentifier identifier :
        doiDao.findActiveByIdentifiersAndType(stored, provider)) {
      // rows come oldest first, and putIfAbsent keeps the oldest, which is the row the
      // single-identifier query would have returned should two ever exist
      linkedByStoredValue.putIfAbsent(identifier.getIdentifier(), identifier);
    }
    for (ApiPidinstRecord hit : hits) {
      Optional<DigitalObjectIdentifier> link =
          storedValuesOf(hit).map(linkedByStoredValue::get).filter(Objects::nonNull).findFirst();
      hit.setAlreadyLinked(link.isPresent());
      hit.setLinkedInstrumentGlobalId(
          link.flatMap(identifier -> visibleGlobalIdOf(identifier, user)).orElse(null));
    }
  }

  /**
   * The Global ID of the instrument holding {@code identifier}, when {@code user} may read it by
   * the same read-or-limited-read rule that decides everywhere else whether a caller gets a record
   * or only its no-access view; empty otherwise (RSDEV-1505).
   *
   * <p>This governs what the search volunteers, not what is secret: {@code GET /instruments/{id}}
   * deliberately answers 200 with a name-only public view rather than 404, so a guessed id still
   * yields the name.
   *
   * <p>A row with no record is defensive: no production path leaves a PIDINST identifier without
   * one. It counts as hidden rather than unlinked, so the PID still reads as taken and the
   * permission check is never handed a null. The refusal then says an instrument the caller cannot
   * access holds the PID, which in that unreachable state names an instrument that is not there.
   */
  private Optional<String> visibleGlobalIdOf(DigitalObjectIdentifier identifier, User user) {
    InventoryRecord holder = identifier.getInventoryRecord();
    if (holder == null || !invPermissions.canUserReadOrLimitedReadInventoryRecord(holder, user)) {
      return Optional.empty();
    }
    return Optional.ofNullable(identifier.getConnectedRecordGlobalIdentifier());
  }

  /**
   * Every value {@code DigitalObjectIdentifier.identifier} may hold for this record, PID first. An
   * imported PID is stored as the PID itself; one this deployment minted through B2INST is stored
   * as the provider's record id, because the Handle does not exist until publish and is never
   * written back. Matching both is what makes the already-linked guarantee hold for a locally
   * minted PID as well as an imported one.
   */
  private static Stream<String> storedValuesOf(ApiPidinstRecord record) {
    return Stream.of(record.getPid(), record.getProviderRecordId()).filter(Objects::nonNull);
  }

  private Optional<DigitalObjectIdentifier> linkedInstrumentOf(
      ApiPidinstRecord record, IdentifierType provider) {
    return storedValuesOf(record)
        .map(value -> doiDao.findActiveByIdentifierAndType(value, provider))
        .flatMap(Optional::stream)
        .findFirst();
  }

  /**
   * The refusal for a PID an instrument already links, naming the instrument only to a caller who
   * may read it, so the refusal keeps a stated reason without disclosing a Global ID the search
   * itself withheld (RSDEV-1505). The permission check picks the key; the controller renders it.
   */
  private PidinstAlreadyLinkedException alreadyLinked(
      DigitalObjectIdentifier identifier, User user) {
    return visibleGlobalIdOf(identifier, user)
        .map(
            globalId ->
                new PidinstAlreadyLinkedException(
                    "errors.inventory.identifier.pidinstAlreadyLinked", globalId))
        .orElseGet(
            () ->
                new PidinstAlreadyLinkedException(
                    "errors.inventory.identifier.pidinstAlreadyLinkedNoAccess"));
  }

  /** Same translation {@code InstrumentsApiController.createNewInstrument} applies to a POST. */
  private static void applyTargetLocation(ApiInstrument toCreate, ApiTargetLocation target) {
    if (target == null) {
      return;
    }
    ApiContainerInfo parentContainer = new ApiContainerInfo();
    parentContainer.setId(target.getContainerId());
    toCreate.setParentContainer(parentContainer);
    toCreate.setParentLocation(target.getContainerLocation());
  }
}
