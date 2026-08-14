package com.stockapp.domain.screening;

import com.stockapp.domain.screening.dto.SearchConditionRequest;
import com.stockapp.domain.screening.dto.SearchConditionRuleRequest;
import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchConditionServiceTest {

    @Mock
    private SearchConditionRepository searchConditionRepository;

    @Mock
    private UserRepository userRepository;

    private SearchConditionService searchConditionService;

    @BeforeEach
    void setUp() {
        searchConditionService = new SearchConditionService(
                searchConditionRepository,
                userRepository);
    }

    @Test
    void softDeleteRecordsCurrentUserAndDisablesCondition() {
        User admin = User.builder()
                .id(3L)
                .email("admin@example.com")
                .password("password")
                .nickname("admin")
                .phoneNumber("encrypted")
                .role(UserRole.ADMIN)
                .build();
        SearchCondition condition = SearchCondition.create(
                "검색식",
                null,
                true,
                100,
                80,
                false,
                admin);
        SearchConditionRule rule = SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE,
                null,
                ScreeningOperator.GREATER_THAN,
                java.math.BigDecimal.ONE,
                null,
                1);
        condition.addRule(rule);

        when(searchConditionRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(condition));
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(admin));

        searchConditionService.deleteSearchCondition(
                1L,
                "admin@example.com");

        assertThat(condition.getDeletedAt()).isNotNull();
        assertThat(condition.getDeletedBy()).isSameAs(admin);
        assertThat(condition.isEnabled()).isFalse();
        assertThat(condition.getRules()).containsExactly(rule);
        verify(searchConditionRepository).flush();
    }

    @Test
    void returnsOnlyDeletedConditionsInRepositoryOrder() {
        User admin = createAdmin();
        SearchCondition condition = createCondition(admin);
        condition.softDelete(admin);

        when(searchConditionRepository
                .findAllByDeletedAtIsNotNullOrderByDeletedAtDesc())
                .thenReturn(List.of(condition));

        var responses = searchConditionService
                .getDeletedSearchConditions();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getDeletedAt()).isNotNull();
        assertThat(responses.get(0).getDeletedById()).isEqualTo(3L);
        assertThat(responses.get(0).getDeletedByEmail())
                .isEqualTo("admin@example.com");
    }

    @Test
    void restoreClearsDeleteMetadataKeepsRulesAndStaysDisabled() {
        User admin = createAdmin();
        SearchCondition condition = createCondition(admin);
        SearchConditionRule rule = SearchConditionRule.createValueRule(
                ScreeningStage.SCREENING,
                ScreeningMetric.CURRENT_PRICE,
                null,
                ScreeningOperator.GREATER_THAN,
                java.math.BigDecimal.ONE,
                null,
                1);
        condition.addRule(rule);
        condition.softDelete(admin);

        when(searchConditionRepository.findByIdAndDeletedAtIsNotNull(1L))
                .thenReturn(Optional.of(condition));

        searchConditionService.restoreSearchCondition(1L);

        assertThat(condition.getDeletedAt()).isNull();
        assertThat(condition.getDeletedBy()).isNull();
        assertThat(condition.isEnabled()).isFalse();
        assertThat(condition.getRules()).containsExactly(rule);
        verify(searchConditionRepository).flush();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositivePeriodEvenWhenBeanValidationIsBypassed(int period) {
        User admin = createAdmin();
        SearchConditionRequest request =
                org.mockito.Mockito.mock(SearchConditionRequest.class);
        SearchConditionRuleRequest rule =
                org.mockito.Mockito.mock(SearchConditionRuleRequest.class);
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(admin));
        when(request.getRules()).thenReturn(List.of(rule));
        when(rule.getStage()).thenReturn(ScreeningStage.SCREENING);
        when(rule.getLeftMetric()).thenReturn(ScreeningMetric.AVERAGE_VOLUME);
        when(rule.getLeftPeriod()).thenReturn(period);
        when(rule.getRuleOrder()).thenReturn(1);

        assertThatThrownBy(() -> searchConditionService.createSearchCondition(
                "admin@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("왼쪽 지표 기간은 1 이상이어야 합니다.");
        verify(searchConditionRepository, never()).saveAndFlush(any());
    }

    private User createAdmin() {
        return User.builder()
                .id(3L)
                .email("admin@example.com")
                .password("password")
                .nickname("admin")
                .phoneNumber("encrypted")
                .role(UserRole.ADMIN)
                .build();
    }

    private SearchCondition createCondition(User admin) {
        return SearchCondition.create(
                "검색식",
                null,
                true,
                100,
                80,
                false,
                admin);
    }
}
