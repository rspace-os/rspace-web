package com.researchspace.model.externalWorkflows;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Getter
@EqualsAndHashCode(of = { "extId","rspacedataid","externalService" })
public class ExternalWorkFlowData implements Serializable {
  public enum ExternalService {
    GALAXY;
  }
  public enum RspaceContainerType {
    DOCUMENT, FIELD;
  }
  public enum RspaceDataType {
    LOCAL, EXTERNAL;
  }
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  /**
   * The service hosting the data
   */
  @NotNull
  @Enumerated(EnumType.STRING)
  private ExternalService externalService;
  /**
   * the rspace id of the data that was uploaded to the external service
   */
  @NotNull
  private long rspacedataid;
  /**
   * whether this is data local to RSpace or External data
   */
  @NotNull
  @Enumerated(EnumType.STRING)
  private RspaceDataType rspaceDataType;

  /**
   * the id of the rspace field/document which was used to initiate data transfer for the external workflow.
   */
  @NotNull
  private long rspacecontainerid;

  /**
   * the name of the rspace field/document which was used to initiate data transfer for the external workflow.
   */
  @NotNull
  private String rspacecontainerName;

  /**
   * the type of RSpace container this data belongs to
   */
  @NotNull
  @Enumerated(EnumType.STRING)
  private RspaceContainerType rspaceContainerType;

  /**
   * Filename for this data on the remote service
   */
  @NotNull
  private String extName;
  /**
   * The ID provided by the external service for this data
   */
  @NotNull
  private String extId;

  /**
   * A secondary ID provided by the external service for this data (optional)
   */
  private String extSecondaryId;

  /**
   * ID for the 'container' (eg folder, history etc) storing the data on the external service
   */
  @NotNull
  private String extContainerID;
  /**
   * Name for the 'container' storing the data on the external service
   */
  @NotNull
  private String extContainerName;
  /**
   * Base url to the service this data was uploaded to
   */
  @NotNull
  private String baseUrl;

  @ManyToMany(mappedBy = "externalWorkFlowData")
  @Setter
  private Set<ExternalWorkFlowInvocation> externalWorkflowInvocations = new HashSet<>();
  @Builder()
  public ExternalWorkFlowData(
      @NonNull ExternalService externalService,  @NonNull long rspacedataid,  @NonNull ExternalWorkFlowData.RspaceDataType rspaceDataType,
      @NonNull long rspacecontainerId,  @NonNull String rspaceContainerName,   @NonNull ExternalWorkFlowData.RspaceContainerType rspaceContainerType,  @NonNull String extName,
      @NonNull String extId,  @NonNull String extSecondaryId,  @NonNull String extContainerID,  @NonNull String extContainerName,  @NonNull String baseUrl
      ){
      this.externalService = externalService;
      this.rspacedataid = rspacedataid;
      this.rspaceDataType = rspaceDataType;
      this.rspacecontainerid = rspacecontainerId;
      this.rspacecontainerName = rspaceContainerName;
      this.rspaceContainerType = rspaceContainerType;
      this.extName = extName;
      this.extId = extId;
      this.extSecondaryId = extSecondaryId;
      this.extContainerID = extContainerID;
      this.extContainerName = extContainerName;
      this.baseUrl = baseUrl;
  }
  protected ExternalWorkFlowData() {
  }

}
