package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.SearchConditionEnabledRequest;
import com.stockapp.domain.screening.dto.DeletedSearchConditionResponse;
import com.stockapp.domain.screening.dto.SearchConditionMetadataResponse;
import com.stockapp.domain.screening.dto.SearchConditionRequest;
import com.stockapp.domain.screening.dto.SearchConditionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/search-conditions")
@RequiredArgsConstructor
public class SearchConditionController {

        private final SearchConditionService searchConditionService;

        @GetMapping("/meta")
        public SearchConditionMetadataResponse getMetadata() {

                return SearchConditionMetadataResponse.create();
        }

        @GetMapping
        public List<SearchConditionResponse> getSearchConditions() {

                return searchConditionService.getSearchConditions();
        }

        @GetMapping("/deleted")
        public List<DeletedSearchConditionResponse> getDeletedSearchConditions() {

                return searchConditionService.getDeletedSearchConditions();
        }

        @GetMapping("/{id}")
        public SearchConditionResponse getSearchCondition(
                        @PathVariable Long id) {

                return searchConditionService
                                .getSearchCondition(id);
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public SearchConditionResponse createSearchCondition(
                        Authentication authentication,
                        @Valid @RequestBody SearchConditionRequest request) {

                return searchConditionService
                                .createSearchCondition(
                                                authentication.getName(),
                                                request);
        }

        @PutMapping("/{id}")
        public SearchConditionResponse updateSearchCondition(
                        @PathVariable Long id,
                        @Valid @RequestBody SearchConditionRequest request) {

                return searchConditionService
                                .updateSearchCondition(
                                                id,
                                                request);
        }

        @PatchMapping("/{id}/enabled")
        public SearchConditionResponse changeEnabled(
                        @PathVariable Long id,
                        @Valid @RequestBody SearchConditionEnabledRequest request) {

                return searchConditionService
                                .changeEnabled(
                                                id,
                                                request.getEnabled());
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void deleteSearchCondition(
                        Authentication authentication,
                        @PathVariable Long id) {

                searchConditionService.deleteSearchCondition(
                                id,
                                authentication.getName());
        }

        @PatchMapping("/{id}/restore")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void restoreSearchCondition(
                        @PathVariable Long id) {

                searchConditionService.restoreSearchCondition(id);
        }
}
