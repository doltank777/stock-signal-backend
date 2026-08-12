package com.stockapp.domain.screening;

import com.stockapp.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "search_conditions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int priority;

    @Column(name = "screening_score", nullable = false)
    private int screeningScore;

    @Column(name = "realtime_enabled", nullable = false)
    private boolean realtimeEnabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "searchCondition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ruleOrder ASC")
    private final List<SearchConditionRule> rules = new ArrayList<>();

    private SearchCondition(
            String name,
            String description,
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled,
            User createdBy) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.priority = priority;
        this.screeningScore = screeningScore;
        this.realtimeEnabled = realtimeEnabled;
        this.createdBy = createdBy;
    }

    public static SearchCondition create(
            String name,
            String description,
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled,
            User createdBy) {
        return new SearchCondition(
                name,
                description,
                enabled,
                priority,
                screeningScore,
                realtimeEnabled,
                createdBy);
    }

    public void update(
            String name,
            String description,
            boolean enabled,
            int priority,
            int screeningScore,
            boolean realtimeEnabled) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.priority = priority;
        this.screeningScore = screeningScore;
        this.realtimeEnabled = realtimeEnabled;
    }

    public void addRule(SearchConditionRule rule) {
        rule.assignSearchCondition(this);
        this.rules.add(rule);
    }

    public void removeRule(SearchConditionRule rule) {
        this.rules.remove(rule);
        rule.assignSearchCondition(null);
    }

    public List<SearchConditionRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    @PrePersist
    public void prePersist() {

        LocalDateTime now = LocalDateTime.now();

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {

        this.updatedAt = LocalDateTime.now();
    }
}