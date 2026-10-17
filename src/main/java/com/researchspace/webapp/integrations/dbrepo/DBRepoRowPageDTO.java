package com.researchspace.webapp.integrations.dbrepo;

import java.util.List;
import java.util.Map;

public record DBRepoRowPageDTO(
    List<Map<String, Object>> rows, int page, int size, Long totalCount) {}
