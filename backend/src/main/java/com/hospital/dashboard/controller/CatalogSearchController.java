package com.hospital.dashboard.controller;

import com.hospital.dashboard.model.SearchPage;
import com.hospital.dashboard.exception.InvalidRequestException;
import com.hospital.dashboard.service.CatalogSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/search")
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class CatalogSearchController {
    private final CatalogSearchService service;
    public CatalogSearchController(CatalogSearchService service) { this.service = service; }

    @GetMapping
    public SearchPage search(@RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) String city,
            @RequestParam(required = false) String category, @RequestParam(required = false) Integer minExperience,
            @RequestParam(required = false) Integer minRating, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (q.length() > 120 || page < 0 || page > 10000 || size < 1 || size > 50 || (minExperience != null && minExperience < 0)
                || (minRating != null && (minRating < 1 || minRating > 5))) throw new InvalidRequestException("Invalid search parameters");
        if (type != null && !java.util.Set.of("doctor", "hospital", "medicine", "test").contains(type)) throw new InvalidRequestException("Unsupported entity type");
        return service.search(q, type, city, category, minExperience, minRating, page, size);
    }

    @GetMapping("/analytics") public Map<String,Object> analytics() { return service.analytics(); }
    @GetMapping("/status") public Map<String,Object> status() { return service.status(); }
    @PostMapping("/reindex") public Map<String,Object> reindex() { return service.reindex(); }
}
