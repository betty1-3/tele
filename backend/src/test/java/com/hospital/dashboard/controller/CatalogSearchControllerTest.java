package com.hospital.dashboard.controller;

import com.hospital.dashboard.exception.InvalidRequestException;
import com.hospital.dashboard.model.SearchPage;
import com.hospital.dashboard.service.CatalogSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogSearchControllerTest {
    @Test
    void forwardsValidatedFiltersAndPagination() {
        CatalogSearchService service = mock(CatalogSearchService.class);
        CatalogSearchController controller = new CatalogSearchController(service);
        SearchPage expected = new SearchPage(List.of(), 0, 2, 10, Map.of());
        when(service.search("heart", "doctor", "Chennai", "CARDIOLOGIST", 4, 5, 2, 10)).thenReturn(expected);

        assertSame(expected, controller.search("heart", "doctor", "Chennai", "CARDIOLOGIST", 4, 5, 2, 10));
        verify(service).search("heart", "doctor", "Chennai", "CARDIOLOGIST", 4, 5, 2, 10);
    }

    @Test
    void rejectsUnboundedOrUnsupportedRequests() {
        CatalogSearchController controller = new CatalogSearchController(mock(CatalogSearchService.class));
        assertThrows(InvalidRequestException.class, () -> controller.search("x".repeat(121), null, null, null, null, null, 0, 10));
        assertThrows(InvalidRequestException.class, () -> controller.search("", null, null, null, null, null, 0, 51));
        assertThrows(InvalidRequestException.class, () -> controller.search("", "patient", null, null, null, null, 0, 10));
        assertThrows(InvalidRequestException.class, () -> controller.search("", null, null, null, null, 6, 0, 10));
    }
}
