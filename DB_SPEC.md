# 📊 Stock Signal App DB 명세서

## KRX trading calendar

`krx_trading_days` materializes the official KIS domestic holiday response
queried through the separately configured real-trading Calendar REST environment.
Both confirmed trading and confirmed closed dates are stored. A missing row
means unavailable calendar coverage, not a closed date.

```sql
CREATE TABLE krx_trading_days (
    trade_date DATE NOT NULL,
    trading_day BIT NOT NULL,
    source VARCHAR(20) NOT NULL,
    synchronized_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (trade_date)
);
```

- `trading_day=1` maps from KIS `opnd_yn=Y`; `0` maps from `opnd_yn=N`.
- `source` is `KIS` in the first KRX-only version.
- `synchronized_at` is the latest official confirmation time and is refreshed
  even when the open/closed value is unchanged.
- Synchronization upserts by `trade_date`; official corrections overwrite the
  previous trading-day flag.
- Manual initial synchronization fetches an explicitly requested range from the
  real-trading KIS Calendar source and upserts only after complete range
  validation succeeds. Partial ranges are never written.
- Runtime calendar reads only this table. Missing coverage fails closed and
  never falls back to a false/closed result.
- There is no automatic synchronization scheduler in this version.

## Daily price finalization execution

`daily_price_finalization_executions` stores one mutable execution summary per
KRX target trade date. It does not store per-stock success items. Recovery
reruns the full KOSPI/KOSDAQ universe because finalization writes are idempotent.

```sql
CREATE TABLE daily_price_finalization_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    target_trade_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    ready BIT NOT NULL,
    attempt_count INT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    target_stock_count INT NOT NULL,
    inserted_stock_count INT NOT NULL,
    updated_stock_count INT NOT NULL,
    unchanged_stock_count INT NOT NULL,
    no_data_stock_count INT NOT NULL,
    failed_stock_count INT NOT NULL,
    api_call_count INT NOT NULL,
    present_row_count INT NOT NULL,
    missing_stock_count INT NOT NULL,
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_price_finalization_execution_trade_date
        UNIQUE (target_trade_date)
);
```

- Status values: `RUNNING`, `COMPLETED`, `FAILED`, `INTERRUPTED`.
- `ready` is separate. `COMPLETED` can have `ready=0` for failed, `NO_DATA`,
  or missing stocks. `ready=1` requires all three counts to be zero.
- Starting/recovering updates this date row to `RUNNING`, increments
  `attempt_count`, clears prior result fields, and commits before KIS calls.
- A JVM/power failure leaves `RUNNING` and `finished_at=NULL`; recovery can
  detect it. Fatal and interrupt outcomes record `finished_at` and `last_error`.
- No FK or per-stock execution-item table is used in this first version.
- The project has no Flyway/Liquibase chain. Local `ddl-auto=update` can create
  this table; schema-validation environments must apply the SQL before deploy.

## Daily history bootstrap execution

`daily_history_bootstrap_executions` keeps an append-only aggregate history of
one-shot historical daily-price bootstrap attempts. An execution for evaluation
date `D` covers the required KRX trading dates before `D`; the exact row for `D`
remains the responsibility of daily-price finalization.

```sql
CREATE TABLE daily_history_bootstrap_executions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evaluation_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL,
    ready BIT NOT NULL,
    required_previous_trading_day_count INT NOT NULL,
    required_trading_date_count INT NOT NULL,
    target_stock_count INT NOT NULL,
    completed_stock_count INT NOT NULL,
    partial_stock_count INT NOT NULL,
    failed_stock_count INT NOT NULL,
    initial_missing_count INT NOT NULL,
    remaining_missing_count INT NOT NULL,
    planned_range_count INT NOT NULL,
    planned_chunk_count INT NOT NULL,
    attempted_chunk_count INT NOT NULL,
    api_call_count INT NOT NULL,
    saved_row_count INT NOT NULL,
    skipped_row_count INT NOT NULL,
    empty_response_chunk_count INT NOT NULL,
    out_of_range_response_row_count INT NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    finished_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    INDEX idx_daily_history_bootstrap_readiness
        (evaluation_date, ready, required_previous_trading_day_count)
);
```

- Status values: `RUNNING`, `COMPLETED`, `COMPLETED_WITH_GAPS`, `FAILED`.
- Every attempt inserts a new row. `evaluation_date` is intentionally not
  unique so retries and executions after requirement changes remain auditable.
- `ready` is copied from `BootstrapDailyHistoryBatchResult.ready()`. It means
  the required history before `D` has no remaining gaps or failed stocks; it
  does not mean the finalization row for `D` is ready.
- Future readiness checks can select a `ready=1` execution for the evaluation
  date whose `required_previous_trading_day_count` is at least the current
  requirement. Latest ordering is `finished_at DESC, id DESC`.
- A zero-history requirement is still persisted as a completed ready execution.
- Start, completion, and failure persistence each use a short independent
  transaction. The KIS/bootstrap batch is not held in a database transaction.
- Only aggregate counts are stored. Problem-stock details remain in the batch
  logs; there is no FK or per-stock child table in this version.
- The project has no Flyway/Liquibase chain. Apply this SQL before using
  `ddl-auto=validate`; do not run it from this one-shot task.

## 1. users

사용자 정보

| 컬럼명                 | 타입             | NULL | 제약조건               | 설명                                |
| ---------------------- | ---------------- | ---: | ---------------------- | ----------------------------------- |
| id                     | BIGINT           |    N | PK, AUTO_INCREMENT     | 사용자 ID                           |
| created_at             | DATETIME(6)      |    N |                        | 가입일시                             |
| email                  | VARCHAR(100)     |    N | UNIQUE                 | 이메일                              |
| nickname               | VARCHAR(50)      |    N |                        | 닉네임                              |
| password               | VARCHAR(255)     |    N |                        | BCrypt 암호화 비밀번호               |
| role                   | ENUM / VARCHAR   |    N |                        | 사용자 권한                          |
| phone_number           | VARCHAR(255)     |    N |                        | AES 암호화 전화번호                  |
| membership_type        | VARCHAR(20)      |    N | DEFAULT `FREE`         | 회원 등급 (`FREE`, `PAID`)           |
| membership_started_at  | TIMESTAMP(6)     |    Y |                        | 유료회원 시작 절대시각               |
| membership_expired_at  | TIMESTAMP(6)     |    Y |                        | 유료회원 만료 절대시각               |

### 인덱스

```sql
PRIMARY KEY (id)
UNIQUE KEY (email)
```

### 권한

```text
USER
ADMIN
```

`/api/admin/**` API는 `ADMIN` 권한 사용자만 접근할 수 있습니다.

### Membership

회원 등급은 보안 권한인 `role`과 분리해 관리합니다.

```text
FREE
→ membership_started_at = NULL
→ membership_expired_at = NULL

PAID
→ membership_started_at IS NOT NULL
→ membership_expired_at IS NOT NULL
→ membership_expired_at > membership_started_at
```

현재 유료 상태는 서버의 UTC 기준 현재 시각 `now`에 대해 다음 조건으로 계산합니다.

```text
membership_type = PAID
AND membership_started_at <= now
AND membership_expired_at > now
```

기간이 만료되어도 `membership_type`을 자동으로 `FREE`로 변경하지 않습니다.

---

# 2. stocks

종목 정보

| 컬럼명         | 타입             | NULL | 제약조건               | 설명    |
| ----------- | -------------- | ---: | ------------------ | ----- |
| id          | BIGINT         |    N | PK, AUTO_INCREMENT | 종목 ID |
| stock_code  | VARCHAR(20)    |    N | UNIQUE             | 종목 코드 |
| stock_name  | VARCHAR(100)   |    N |                    | 종목명   |
| market_type | ENUM / VARCHAR |    N |                    | 시장 구분 |

시장 구분:

```text
KOSPI
KOSDAQ
KONEX
```

### 인덱스

```sql
PRIMARY KEY (id)
UNIQUE KEY (stock_code)
```

---

# 3. stock_prices

주식 현재가 수집 데이터

`Stock` Entity와 FK 관계를 두지 않고 `stock_code` 문자열 기준으로 관리합니다.

| 컬럼명           | 타입          | NULL | 제약조건               | 설명     |
| ------------- | ----------- | ---: | ------------------ | ------ |
| id            | BIGINT      |    N | PK, AUTO_INCREMENT | 데이터 ID |
| stock_code    | VARCHAR(20) |    N |                    | 종목 코드  |
| current_price | BIGINT      |    N |                    | 현재가    |
| change_rate   | DOUBLE      |    N |                    | 등락률    |
| volume        | BIGINT      |    N |                    | 누적 거래량 |
| trade_date    | DATE        |    N |                    | 거래일    |
| collected_at  | DATETIME(6) |    N |                    | 수집일시   |

### 인덱스

```sql
PRIMARY KEY (id)
KEY idx_stock_prices_stock_trade_collected_id
    (stock_code, trade_date, collected_at, id)
```

특정 종목과 거래일의 최신 스냅샷 한 건을
`collected_at DESC, id DESC`로 조회할 때 사용하는 복합 index입니다.
MySQL 8은 동일 방향의 정렬 열을 오름차순 index에서 역방향으로 탐색할 수 있으므로,
별도의 `DESC` 방향은 지정하지 않습니다.

### 데이터 수집 흐름

```text
StockPriceScheduler
→ KIS REST API
→ stock_prices 저장
```

전체 국내 종목을 순차적으로 수집할 수 있는 구조로 사용합니다.

향후 전체 종목 Screening의 기초 데이터로 활용할 예정입니다.

### API

```http
GET /api/stocks/{stockCode}/price/latest
```

---

# 3-1. stock_daily_prices

종목별 KRX 정규장 종료 후 확정된 최종 일봉 OHLCV 데이터입니다.

`stock_prices`가 장중 현재가 스냅샷을 저장하는 것과 달리,
`stock_daily_prices`는 종목별·KRX 거래일별 하나의 확정 일봉을 저장합니다.
일반 최초/증분 적재는 기존 날짜를 변경하지 않는 insert-only 방식이며,
Finalization은 caller가 명시한 단일 거래일에 한해서 기존 OHLCV를 갱신할 수 있습니다.
Finalization batch 실행 상태는 별도 테이블에 저장하지 않으며, 일봉 completeness는
batch 대상 종목과 해당 거래일 row의 실제 존재 여부를 비교해 판단합니다.
현재 row에는 별도의 `finalized` 상태 컬럼이 없습니다.

| 컬럼명       | 타입         | NULL | 제약조건                         | 설명          |
| ------------ | ------------ | ---: | -------------------------------- | ------------- |
| id           | BIGINT       |    N | PK, AUTO_INCREMENT               | 일봉 ID       |
| stock_id     | BIGINT       |    N | FK, UNIQUE 복합키 구성           | 종목 ID       |
| trade_date   | DATE         |    N | UNIQUE 복합키 구성               | 거래일        |
| open_price   | BIGINT       |    N |                                  | 시가          |
| high_price   | BIGINT       |    N |                                  | 고가          |
| low_price    | BIGINT       |    N |                                  | 저가          |
| close_price  | BIGINT       |    N |                                  | 종가          |
| volume       | BIGINT       |    N |                                  | 확정 일 거래량 |
| collected_at | DATETIME(6)  |    N |                                  | 최초 수집일시 (Finalization UPDATE 시 유지) |

### FK

```sql
stock_daily_prices.stock_id
→ stocks.id
```

### Unique Constraint

```sql
UNIQUE KEY uk_stock_daily_prices_stock_trade_date (stock_id, trade_date)
```

동일 종목과 동일 거래일의 일봉은 하나만 저장합니다. 이 복합 unique index는
특정 종목의 기준일 이전 최근 N거래일을 `trade_date DESC`로 조회할 때도 우선 활용합니다.
별도의 중복 index는 두지 않습니다.

향후 Screening 기간 Metric에서 다음 값의 기반 데이터로 사용합니다.

* `AVERAGE_VOLUME(N)`: 기준일 이전 N거래일의 `volume` 평균
* `VOLUME_RATIO(N)`: 장중 누적 거래량을 위 평균 거래량으로 나눈 값
* `MOVING_AVERAGE(N)`: 기준일 이전 N거래일의 `close_price` 평균

---

# 4. signals

특정 종목이 특정 관리자 SearchCondition의 SIGNAL 조건에 언제 일치했는지를 기록하는 event history입니다.

| 컬럼명                  | 타입           | NULL | 제약조건               | 설명                 |
| -------------------- | ------------ | ---: | ------------------ | ------------------ |
| id                   | BIGINT       |    N | PK, AUTO_INCREMENT | Signal ID          |
| stock_id             | BIGINT       |    N | FK                 | 조건에 일치한 종목 ID      |
| search_condition_id  | BIGINT       |    N | FK                 | 일치한 관리자 검색식 ID     |
| message              | VARCHAR(255) |    N |                    | Signal 설명          |
| detected_at          | DATETIME(6)  |    N |                    | KIS WebSocket 체결 시각 |

### FK

```sql
signals.stock_id
→ stocks.id

signals.search_condition_id
→ search_conditions.id
```

`stock_id`와 `search_condition_id`는 모두 필수입니다. 검색식은 soft delete되므로
SearchCondition FK는 물리 삭제를 허용하지 않는 `ON DELETE RESTRICT`,
`ON UPDATE RESTRICT` 정책을 사용합니다.

### Index

```sql
idx_signals_stock_condition_detected_at
(stock_id, search_condition_id, detected_at)

fk_signals_search_condition
(search_condition_id)
```

복합 인덱스는 종목 + 검색식 + 발생 시각 기준의 최근 30분 cooldown 조회에 사용합니다.
SearchCondition FK를 지원하는 단독 인덱스도 유지합니다. 실제 constraint와 index 이름은
MySQL의 최신 `SHOW CREATE TABLE signals` 결과를 기준으로 확인합니다.

### 중복 방지

```text
동일 종목
+ 동일 SearchCondition
+ 최근 30분
→ Signal 중복 생성 방지
```

한 체결에서 여러 검색식이 일치하면 검색식별로 Signal을 한 건씩 생성합니다. 삭제되었거나
`enabled = false` 또는 `realtime_enabled = false`인 검색식에는 신규 Signal을 저장하지 않습니다.
현재 중복 확인과 INSERT는 하나의 로컬 transaction 안에서 수행하지만 범위 조건을 DB unique
constraint로 표현할 수 없어, 여러 프로세스나 동시 callback 사이의 완전한 원자성은 보장하지 않습니다.

---

# 5. favorites

사용자 관심종목

동일 사용자가 같은 종목을 중복 등록할 수 없도록 `user_id + stock_id` 유니크 제약을 사용합니다.

| 컬럼명        | 타입          | NULL | 제약조건               | 설명      |
| ---------- | ----------- | ---: | ------------------ | ------- |
| id         | BIGINT      |    N | PK, AUTO_INCREMENT | 관심종목 ID |
| user_id    | BIGINT      |    N | FK                 | 사용자 ID  |
| stock_id   | BIGINT      |    N | FK                 | 종목 ID   |
| created_at | DATETIME(6) |    N |                    | 등록일시    |

### 인덱스

```sql
PRIMARY KEY (id)
UNIQUE KEY (user_id, stock_id)
```

### FK

```sql
favorites.user_id
→ users.id

favorites.stock_id
→ stocks.id
```

### API

```http
GET /api/favorites

POST /api/favorites/{stockCode}

DELETE /api/favorites/{stockCode}
```

---

# 6. search_conditions

관리자가 등록하는 검색식 기본 정보입니다.

하나의 `search_conditions` 데이터는 여러 `search_condition_rules`를 가질 수 있습니다.

관계:

```text
search_conditions
        1
        │
        │
        N
search_condition_rules
```

### 컬럼

| 컬럼명              | 타입           | NULL | 제약조건               | 설명                     |
| ---------------- | ------------ | ---: | ------------------ | ---------------------- |
| id               | BIGINT       |    N | PK, AUTO_INCREMENT | 검색식 ID                 |
| name             | VARCHAR(100) |    N |                    | 검색식 이름                 |
| description      | VARCHAR(500) |    Y |                    | 검색식 설명                 |
| enabled          | BOOLEAN      |    N |                    | 검색식 활성 여부              |
| priority         | INT          |    N |                    | 검색식 우선순위               |
| screening_score  | INT          |    N |                    | Candidate 기본 점수        |
| realtime_enabled | BOOLEAN      |    N |                    | WebSocket 실시간 감시 사용 여부 |
| created_by       | BIGINT       |    Y | FK                 | 검색식 등록 관리자             |
| created_at       | DATETIME(6)  |    N |                    | 생성일시                   |
| updated_at       | DATETIME(6)  |    N |                    | 수정일시                   |
| deleted_at       | DATETIME(6)  |    Y |                    | Soft Delete 처리 일시. NULL이면 정상 데이터 |
| deleted_by_id    | BIGINT       |    Y | FK                 | 삭제한 관리자 ID. NULL이면 삭제되지 않은 데이터 |

### FK

```sql
search_conditions.created_by
→ users.id

search_conditions.deleted_by_id
→ users.id
```

검색식 삭제는 물리 DELETE가 아닌 Soft Delete를 사용합니다. 삭제 시 `deleted_at`과
`deleted_by_id`를 기록하고 `enabled = false`로 변경하며, Rule은 보존합니다. 일반 목록과
상세/수정/활성 상태 변경 대상은 `deleted_at IS NULL`인 검색식으로 제한합니다.

### 주요 필드

#### enabled

검색식 활성 여부입니다.

```text
true
→ 활성

false
→ 비활성
```

검색식은 기본적으로 DELETE보다 활성/비활성 방식으로 관리합니다.

#### priority

검색식 자체의 중요도를 나타냅니다.

향후 여러 검색식의 후보를 통합할 때 Priority 계산에 활용할 수 있습니다.

#### screening_score

해당 검색식에 부합한 종목에게 부여할 Candidate 기본 점수입니다.

현재는 검색식 관리 데이터로 저장하며 실제 Candidate 계산은 향후 Screening Engine에서 구현합니다.

#### realtime_enabled

```text
true
→ SCREENING 후 WebSocket 실시간 감시 및 SIGNAL 조건 사용

false
→ REST 기반 검색식으로 활용 가능
```

현재 실제 WebSocket 후보 선정 엔진은 아직 구현되지 않았습니다.

---

# 7. search_condition_rules

검색식의 개별 조건을 저장합니다.

예:

```text
거래량 비율 >= 1.5
```

또는:

```text
현재가 > 5일 이동평균
```

과 같은 Rule 하나가 한 행으로 저장됩니다.

### 컬럼

| 컬럼명                 | 타입            | NULL | 제약조건               | 설명               |
| ------------------- | ------------- | ---: | ------------------ | ---------------- |
| id                  | BIGINT        |    N | PK, AUTO_INCREMENT | Rule ID          |
| search_condition_id | BIGINT        |    N | FK                 | 검색식 ID           |
| stage               | VARCHAR(20)   |    N |                    | 조건 단계            |
| left_metric         | VARCHAR(50)   |    N |                    | 왼쪽 지표            |
| left_period         | INT           |    Y |                    | 왼쪽 지표 기간         |
| operator            | VARCHAR(50)   |    N |                    | 비교 연산자           |
| right_type          | VARCHAR(20)   |    N |                    | 오른쪽 비교 타입        |
| right_value         | DECIMAL(19,6) |    Y |                    | 고정 비교 값          |
| right_metric        | VARCHAR(50)   |    Y |                    | 오른쪽 비교 지표        |
| right_period        | INT           |    Y |                    | 오른쪽 지표 기간        |
| logical_operator    | VARCHAR(10)   |    Y |                    | AND / OR         |
| rule_order          | INT           |    N |                    | 같은 Stage 내 조건 순서 |

### FK

```sql
search_condition_rules.search_condition_id
→ search_conditions.id
```

---

## stage

검색식 Rule의 실행 단계를 의미합니다.

```text
SCREENING
SIGNAL
```

### SCREENING

전체 종목에서 후보를 선정하기 위한 1차 조건입니다.

향후 목표:

```text
전체 종목
→ SCREENING
→ Candidate
```

### SIGNAL

WebSocket 실시간 감시 종목에서 최종 Signal 발생 여부를 검사하기 위한 조건입니다.

향후 목표:

```text
Candidate
→ WebSocket
→ SIGNAL
→ Signal
```

---

# ScreeningMetric

현재 지원 지표:

```text
CURRENT_PRICE
CHANGE_RATE
VOLUME
AVERAGE_VOLUME
VOLUME_RATIO
MOVING_AVERAGE
```

| Metric           | 설명                  | Period |
| ---------------- | ------------------- | ------ |
| `CURRENT_PRICE`  | 현재가                 | 불필요    |
| `CHANGE_RATE`    | 등락률                 | 불필요    |
| `VOLUME`         | 현재 거래량              | 불필요    |
| `AVERAGE_VOLUME` | 평균 거래량              | 필요     |
| `VOLUME_RATIO`   | 평균 거래량 대비 현재 거래량 비율 | 필요     |
| `MOVING_AVERAGE` | 이동평균                | 필요     |

예:

```text
VOLUME_RATIO
left_period = 20
```

은 20일 기준 평균 거래량 대비 비율을 의미하는 검색식 표현으로 사용합니다.

---

# ScreeningOperator

지원 비교 연산자:

```text
GREATER_THAN
GREATER_THAN_OR_EQUAL
LESS_THAN
LESS_THAN_OR_EQUAL
EQUAL
```

의미:

```text
GREATER_THAN
>

GREATER_THAN_OR_EQUAL
>=

LESS_THAN
<

LESS_THAN_OR_EQUAL
<=

EQUAL
=
```

---

# ScreeningRightType

오른쪽 비교 대상을 구분합니다.

```text
VALUE
METRIC
```

## VALUE

고정값과 비교합니다.

예:

```text
CHANGE_RATE >= 3
```

DB 표현:

```text
left_metric = CHANGE_RATE
operator = GREATER_THAN_OR_EQUAL

right_type = VALUE
right_value = 3
```

## METRIC

다른 지표와 비교합니다.

예:

```text
CURRENT_PRICE > MOVING_AVERAGE(5)
```

DB 표현:

```text
left_metric = CURRENT_PRICE

operator = GREATER_THAN

right_type = METRIC
right_metric = MOVING_AVERAGE
right_period = 5
```

---

# ScreeningLogicalOperator

여러 조건의 연결 연산자:

```text
AND
OR
```

예:

```text
Rule 1
VOLUME_RATIO >= 1.5

Rule 2
AND CHANGE_RATE >= 3
```

각 Stage의 첫 Rule은 `logical_operator = NULL`을 사용합니다.

두 번째 Rule부터 `AND` 또는 `OR`가 필요합니다.

현재 MVP에서는 단순 Rule 연결을 지원합니다.

복잡한 조건:

```text
A AND (B OR C)
```

은 향후 Condition Group 구조를 추가해 확장할 수 있습니다.

---

# Rule 순서

`rule_order`는 Stage별로 독립적으로 관리합니다.

예:

```text
SCREENING
rule_order = 1
rule_order = 2

SIGNAL
rule_order = 1
rule_order = 2
```

각 Stage는 `1`부터 연속된 순서를 가져야 합니다.

---

# 검색식 예시

## 거래량 급증 + 상승 검색식

### search_conditions

```text
name
= 거래량 급증 상승주

enabled
= true

priority
= 100

screening_score
= 80

realtime_enabled
= true
```

### SCREENING Rule 1

```text
stage
= SCREENING

left_metric
= VOLUME_RATIO

left_period
= 20

operator
= GREATER_THAN_OR_EQUAL

right_type
= VALUE

right_value
= 1.5

rule_order
= 1
```

### SCREENING Rule 2

```text
stage
= SCREENING

left_metric
= CHANGE_RATE

operator
= GREATER_THAN_OR_EQUAL

right_type
= VALUE

right_value
= 3

logical_operator
= AND

rule_order
= 2
```

### SIGNAL Rule

```text
stage
= SIGNAL

left_metric
= VOLUME_RATIO

left_period
= 20

operator
= GREATER_THAN_OR_EQUAL

right_type
= VALUE

right_value
= 2

rule_order
= 1
```

---

# 관리자 검색식 API

모든 API는 `ADMIN` 권한이 필요합니다.

## 목록 조회

```http
GET /api/admin/search-conditions
```

## 상세 조회

```http
GET /api/admin/search-conditions/{id}
```

## 등록

```http
POST /api/admin/search-conditions
```

## 수정

```http
PUT /api/admin/search-conditions/{id}
```

## 활성 / 비활성

```http
PATCH /api/admin/search-conditions/{id}/enabled
```

## 삭제

```http
DELETE /api/admin/search-conditions/{id}
```

현재 로그인한 ADMIN을 삭제자로 기록하는 Soft Delete API입니다.

## 검색식 Metadata

```http
GET /api/admin/search-conditions/meta
```

Metadata API는 관리자 화면에서 사용할 수 있는:

```text
Metric
Operator
RightType
LogicalOperator
Stage
```

정보를 제공합니다.

---

# ERD

```text
users
 ├─ id (PK)
 └─ role

stocks
 ├─ id (PK)
 ├─ stock_code
 ├─ stock_name
 └─ market_type

stock_prices
 ├─ id (PK)
 ├─ stock_code
 └─ current_price

signals
 ├─ id (PK)
 ├─ stock_id (FK)
 ├─ search_condition_id (FK, NOT NULL)
 ├─ message
 └─ detected_at

favorites
 ├─ id (PK)
 ├─ user_id (FK)
 └─ stock_id (FK)

search_conditions
 ├─ id (PK)
 ├─ created_by (FK)
 ├─ deleted_by_id (FK)
 ├─ deleted_at
 ├─ enabled
 ├─ priority
 ├─ screening_score
 └─ realtime_enabled

search_condition_rules
 ├─ id (PK)
 ├─ search_condition_id (FK)
 ├─ stage
 ├─ left_metric
 ├─ operator
 ├─ right_type
 └─ rule_order
```

관계:

```text
stocks (1)
    │
    ├── signals (N)
    │
    └── favorites (N)

users (1)
    │
    ├── favorites (N)
    │
    └── search_conditions (N)

search_conditions (1)
    │
    ├── search_condition_rules (N)
    └── signals (N)
```

`stock_prices`는 현재 `stocks`와 FK 관계가 없습니다.

`signals.search_condition_id`는 `search_conditions.id`를 NOT NULL FK로 참조합니다.

---

# 현재 구현 상태

## 사용자 / 인증

* 회원가입
* 로그인
* JWT 인증
* 현재 사용자 조회
* 전화번호 AES 암호화
* USER / ADMIN Role
* `/api/admin/**` ADMIN 권한 제한

## 종목

* 종목 검색
* 종목 상세
* KIS 현재가 조회
* 현재가 DB 저장
* 최신 현재가 조회

## 관심종목

* 등록
* 삭제
* 목록 조회

## Signal

* 거래량 급증
* 이동평균 돌파
* Signal DB 저장
* 최근 30분 중복 방지
* 실시간 Signal 연동
* Push 연동
* 검색식별 실시간 Signal 저장
* 종목 + 검색식 기준 최근 30분 중복 방지

## 검색식 관리

* `search_conditions`
* `search_condition_rules`
* 검색식 등록
* 검색식 목록 조회
* 검색식 상세 조회
* 검색식 수정
* 검색식 활성 / 비활성
* ADMIN 권한 보호
* 검색식 Metadata API

---

# 향후 DB 관련 작업

DB 기반 검색식의 SCREENING/SIGNAL 평가와 검색식별 Signal 저장 경로가 연결되어 있습니다.

향후 구조:

```text
search_conditions
+
search_condition_rules
        ↓
활성 검색식 조회
        ↓
Screening Engine
        ↓
전체 종목 검사
        ↓
Candidate
        ↓
Priority Score
        ↓
Top 40
        ↓
KIS WebSocket
        ↓
SIGNAL Rule 검사
        ↓
signals
```

향후 추가 검토:

* 검색식 변경 이력 관리
* 검색식 Signal 저장 후 사용자별 Push 연결
* 동시 callback/다중 인스턴스의 cooldown 원자성 보강
* 복잡한 AND / OR 조건 그룹
* Candidate 저장 테이블 여부
* WebSocket 구독 상태 관리
* `stock_prices` 90일 보관 정책
* 오래된 현재가 데이터 자동 삭제
* 일 단위 집계 테이블
* 주요 조회 인덱스 최적화
