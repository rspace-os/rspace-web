package com.researchspace.model.dmps.argos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One page of Argos plans and the total number that match the search. */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTableData<T> {

  private List<T> data;

  private Integer totalCount;
}
