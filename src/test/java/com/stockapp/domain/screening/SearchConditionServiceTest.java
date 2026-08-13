package com.stockapp.domain.screening;

import com.stockapp.domain.user.User;
import com.stockapp.domain.user.UserRepository;
import com.stockapp.domain.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
}
