package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.validation.Errors;

/**
 * The six operations that create a sample from their origins. Each contributes its provenance
 * relation, and optionally a text field or a storage temperature; everything else about the created
 * sample is the same for all of them.
 */
public abstract class CreatingOperation<R extends ApiInventoryOperationRequests.Creating>
    implements InventoryOperation<R> {

  /** Absent means one, so a caller that only wants a single subsample may omit the count. */
  private static final int DEFAULT_COUNT = 1;

  static final String DOCUMENTATION_FIELD_KEY = "operations.documentation.fieldName";

  /** The relation the created sample carries back to each origin, e.g. {@code IsPartOf}. */
  protected abstract String linkRelation();

  /** The i18n key naming that link field; it may interpolate {@code originName}. */
  protected abstract String linkFieldNameKey();

  /**
   * What that key interpolates beyond {@code originName}, for an operation whose link field name
   * quotes one of the caller's own values (Derive's process name). The wizard's confirmation screen
   * renders the same pattern against the whole request, so anything it shows there has to be
   * supplied here too or the stored name keeps the placeholder.
   */
  protected Map<String, Object> linkFieldNameArgs(R request) {
    return Map.of();
  }

  /** Text fields the operation adds to the created sample; empty for most. */
  protected List<ApiExtraField> textFields(
      R request, List<OriginState> origins, LabelResolver labels) {
    return List.of();
  }

  /** The temperature the created sample is stored at, or null when the operation sets none. */
  protected ApiQuantityInfo storageTemp(R request) {
    return null;
  }

  /**
   * What this operation takes from one origin. The default is the amount the caller chose for it;
   * an operation deciding for itself overrides this.
   */
  protected ApiQuantityInfo amountTakenFrom(R request, OriginState origin, int index) {
    ApiQuantityInfo chosen = request.originList().get(index).getAmountTaken();
    return chosen == null
        ? Amounts.noneFrom(origin, request.getEachAmount())
        : Amounts.copy(chosen);
  }

  @Override
  public void validate(R request, Errors errors) {
    OperationQuantityRules.createdAmount(request.getEachAmount(), "eachAmount", errors);
    OperationQuantityRules.totalStorable(
        request.getCount() == null ? java.math.BigDecimal.ONE : request.getCount(),
        request.getEachAmount(),
        "eachAmount",
        errors);
  }

  @Override
  public ApiInventoryOperationPost build(
      R request, List<OriginState> origins, LabelResolver labels, LocalDate today) {
    ApiInventoryOperationPost built = new ApiInventoryOperationPost();
    built.setEmptiesOrigin(emptiesOrigin(request));
    for (int i = 0; i < origins.size(); i++) {
      ApiInventoryOperationOriginUpdate update = new ApiInventoryOperationOriginUpdate();
      update.setId(origins.get(i).id());
      update.setAmountTaken(amountTakenFrom(request, origins.get(i), i));
      built.getOrigins().add(update);
    }
    built.setNewSample(newSample(request, origins, labels));
    return built;
  }

  private ApiSampleWithFullSubSamples newSample(
      R request, List<OriginState> origins, LabelResolver labels) {
    // Origins can share a name (pooling), so the composed link names are deduplicated rather than
    // assumed unique: a record cannot hold two fields with the same name.
    List<ApiExtraField> fields = new ArrayList<>();
    for (OriginState origin : origins) {
      Map<String, Object> args = new HashMap<>();
      args.put("originName", origin.name());
      args.putAll(linkFieldNameArgs(request));
      fields.add(
          OperationFieldNames.link(
              labels.resolve(linkFieldNameKey(), args),
              linkFieldNameKey(),
              linkRelation(),
              origin.globalId()));
    }
    if (request.getDocumentedByGlobalId() != null) {
      fields.add(
          OperationFieldNames.link(
              labels.resolve(DOCUMENTATION_FIELD_KEY),
              OperationFieldNames.DOCUMENTATION_LINK_KEY,
              "IsDocumentedBy",
              request.getDocumentedByGlobalId()));
    }
    fields.addAll(textFields(request, origins, labels));

    ApiSampleWithFullSubSamples sample = new ApiSampleWithFullSubSamples(request.getSampleName());
    sample.setTemplateId(request.getTemplateId());
    sample.getExtraFields().addAll(OperationFieldNames.withUniqueFieldNames(fields));
    int count = request.getCount() == null ? DEFAULT_COUNT : request.getCount().intValue();
    for (int i = 0; i < count; i++) {
      ApiSubSample subSample = new ApiSubSample();
      subSample.setQuantity(Amounts.copy(request.getEachAmount()));
      sample.getSubSamples().add(subSample);
    }
    ApiQuantityInfo storageTemp = storageTemp(request);
    if (storageTemp != null) {
      sample.setStorageTempMin(Amounts.copy(storageTemp));
      sample.setStorageTempMax(Amounts.copy(storageTemp));
    }
    return sample;
  }
}
