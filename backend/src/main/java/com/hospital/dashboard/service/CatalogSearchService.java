package com.hospital.dashboard.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.hospital.dashboard.model.SearchPage;
import com.hospital.dashboard.exception.SearchUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class CatalogSearchService {
    private final ElasticsearchClient client;
    private final JdbcTemplate jdbc;
    private final String index;
    private final AtomicReference<Map<String, Object>> syncStatus = new AtomicReference<>(Map.of("state", "not_initialized"));

    public CatalogSearchService(ElasticsearchClient client, JdbcTemplate jdbc,
            @Value("${app.elasticsearch.index:telemedical-catalog-v1}") String index) {
        this.client = client; this.jdbc = jdbc; this.index = index;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>(syncStatus.get());
        try { result.put("connected", client.ping().value()); result.put("index", index); }
        catch (Exception e) { result.put("connected", false); result.put("index", index); result.put("message", "Elasticsearch is unavailable"); }
        return result;
    }

    public synchronized Map<String, Object> reindex() {
        try {
            ensureIndex();
            List<Map<String, Object>> docs = loadCatalog();
            List<String> ids = docs.stream().map(doc -> doc.get("entityType") + "-" + doc.get("entityId")).toList();
            for (int start = 0; start < docs.size(); start += 500) {
                BulkRequest.Builder bulk = new BulkRequest.Builder();
                docs.subList(start, Math.min(start + 500, docs.size())).forEach(doc -> bulk.operations(BulkOperation.of(op ->
                        op.index(i -> i.index(index).id(doc.get("entityType") + "-" + doc.get("entityId")).document(doc)))));
                var response = client.bulk(bulk.build());
                if (response.errors()) throw new IllegalStateException("Elasticsearch rejected one or more catalog records");
            }
            if (ids.isEmpty()) client.deleteByQuery(d -> d.index(index).query(q -> q.matchAll(m -> m)));
            else client.deleteByQuery(d -> d.index(index).query(q -> q.bool(b -> b.mustNot(m -> m.ids(i -> i.values(ids))))));
            client.indices().refresh(r -> r.index(index));
            Map<String, Object> state = Map.of("state", "ready", "connected", true, "index", index, "documents", docs.size(), "lastSync", java.time.Instant.now().toString());
            syncStatus.set(state); return state;
        } catch (Exception e) {
            syncStatus.set(Map.of("state", "error", "connected", false, "index", index, "message", "Catalog synchronization failed"));
            throw new SearchUnavailableException("Elasticsearch catalog synchronization failed", e);
        }
    }

    @Scheduled(fixedDelayString = "${app.elasticsearch.sync-interval-ms:300000}", initialDelayString = "${app.elasticsearch.sync-initial-delay-ms:30000}")
    public void scheduledSync() {
        try { reindex(); } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduled catalog sync failed; search index may be stale"); }
    }

    public SearchPage search(String q, String type, String city, String category, Integer minExperience,
                             Integer minRating, int page, int size) {
        try {
            ensureIndex();
            List<Query> filters = new ArrayList<>();
            if (type != null) filters.add(Query.of(x -> x.term(t -> t.field("entityType").value(type))));
            if (city != null) filters.add(Query.of(x -> x.term(t -> t.field("city.keyword").value(city))));
            if (category != null) filters.add(Query.of(x -> x.term(t -> t.field("category").value(category))));
            if (minExperience != null) filters.add(Query.of(x -> x.range(r -> r.number(n -> n.field("experience").gte(minExperience.doubleValue())))));
            if (minRating != null) filters.add(Query.of(x -> x.range(r -> r.number(n -> n.field("rating").gte(minRating.doubleValue())))));
            Query query = Query.of(x -> x.bool(b -> {
                if (!filters.isEmpty()) b.filter(filters);
                if (q != null && !q.isBlank()) b.must(m -> m.multiMatch(mm -> mm.query(q).fields("name^3", "description", "category^2", "city", "area", "street").fuzziness("AUTO")));
                return b;
            }));
            SearchResponse<Map> response = client.search(s -> s.index(index).from(page * size).size(size).query(query)
                    .highlight(h -> h.fields("name", f -> f).fields("description", f -> f)), Map.class);
            List<Map<String, Object>> results = new ArrayList<>();
            response.hits().hits().forEach(hit -> {
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source == null) return;
                Map<String, Object> item = new LinkedHashMap<>(source);
                item.put("score", hit.score());
                if (hit.highlight() != null) item.put("highlights", hit.highlight());
                results.add(item);
            });
            return new SearchPage(results, response.hits().total() == null ? 0 : response.hits().total().value(), page, size, Map.of());
        } catch (Exception e) { throw new SearchUnavailableException("Elasticsearch search is unavailable", e); }
    }

    public Map<String, Object> analytics() {
        try {
            SearchResponse<Void> response = client.search(s -> s.index(index).size(0).aggregations("by_type", a -> a.terms(t -> t.field("entityType")))
                    .aggregations("by_city", a -> a.terms(t -> t.field("city.keyword").size(20)))
                    .aggregations("by_category", a -> a.terms(t -> t.field("category").size(20)))
                    .aggregations("average_experience", a -> a.avg(v -> v.field("experience")))
                    .aggregations("average_rating", a -> a.avg(v -> v.field("rating"))), Void.class);
            return Map.of("total", response.hits().total().value(), "byType", buckets(response.aggregations().get("by_type").sterms().buckets().array()),
                    "byCity", buckets(response.aggregations().get("by_city").sterms().buckets().array()),
                    "byCategory", buckets(response.aggregations().get("by_category").sterms().buckets().array()),
                    "averageExperience", response.aggregations().get("average_experience").avg().value(),
                    "averageRating", response.aggregations().get("average_rating").avg().value());
        } catch (Exception e) { throw new SearchUnavailableException("Elasticsearch analytics are unavailable", e); }
    }

    private List<Map<String, Object>> buckets(List<co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket> buckets) {
        return buckets.stream().map(b -> Map.<String, Object>of("label", b.key().stringValue(), "count", b.docCount())).toList();
    }

    private List<Map<String, Object>> loadCatalog() {
        List<Map<String, Object>> all = new ArrayList<>();
        jdbc.query("SELECT doctor_id, first_name, last_name, experience FROM doctor ORDER BY doctor_id", rs -> {
            Map<String, Object> d = doc("doctor", rs.getLong("doctor_id"), rs.getString("first_name") + " " + rs.getString("last_name"));
            d.put("experience", rs.getInt("experience"));
            List<Map<String,Object>> specs = jdbc.queryForList("SELECT specialization FROM doctor_specialization WHERE doctor_id = ?", rs.getLong("doctor_id"));
            if (!specs.isEmpty()) d.put("category", specs.get(0).get("SPECIALIZATION"));
            List<Map<String,Object>> ratings = jdbc.queryForList("SELECT rating FROM doctor_rating WHERE doctor_id = ?", rs.getLong("doctor_id"));
            if (!ratings.isEmpty()) d.put("rating", ((Number) ratings.get(0).get("RATING")).intValue());
            all.add(d);
        });
        jdbc.query("SELECT hospital_id, name, street, area, city, pincode FROM hospital ORDER BY hospital_id", rs -> {
            Map<String, Object> d = doc("hospital", rs.getLong("hospital_id"), rs.getString("name"));
            d.put("street", rs.getString("street")); d.put("area", rs.getString("area")); d.put("city", rs.getString("city")); d.put("pincode", rs.getString("pincode"));
            d.put("description", String.join(" ", safe(rs.getString("street")), safe(rs.getString("area")), safe(rs.getString("city")), safe(rs.getString("pincode")))); all.add(d);
        });
        jdbc.query("SELECT medicine_id, medicine_name, medicine_type FROM medicine ORDER BY medicine_id", rs -> { Map<String,Object>d=doc("medicine",rs.getLong(1),rs.getString(2)); d.put("category",rs.getString(3)); all.add(d); });
        jdbc.query("SELECT test_id, test_name, test_type FROM test ORDER BY test_id", rs -> { Map<String,Object>d=doc("test",rs.getLong(1),rs.getString(2)); d.put("category",rs.getString(3)); all.add(d); });
        return all;
    }
    private Map<String,Object> doc(String type, long id, String name) { Map<String,Object>d=new LinkedHashMap<>(); d.put("entityType",type);d.put("entityId",id);d.put("name",name);return d; }
    private String safe(String s) { return s == null ? "" : s; }

    private void ensureIndex() throws Exception {
        if (client.indices().exists(ExistsRequest.of(r -> r.index(index))).value()) return;
        Map<String,Object> properties = new LinkedHashMap<>();
        properties.put("entityType", Map.of("type","keyword")); properties.put("entityId",Map.of("type","long"));
        for (String field : List.of("name","description","street","area","city","pincode")) properties.put(field,Map.of("type","text","fields",Map.of("keyword",Map.of("type","keyword","ignore_above",256))));
        properties.put("category",Map.of("type","keyword")); properties.put("experience",Map.of("type","integer")); properties.put("rating",Map.of("type","integer"));
        String mapping = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("properties", properties));
        client.indices().create(c -> c.index(index).mappings(m -> m.withJson(new java.io.StringReader(mapping))));
    }
}
