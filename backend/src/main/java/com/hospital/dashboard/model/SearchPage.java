package com.hospital.dashboard.model;

import java.util.List;
import java.util.Map;

public record SearchPage(List<Map<String, Object>> results, long total, int page, int size,
                         Map<String, List<Map<String, Object>>> aggregations) { }
