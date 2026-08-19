# 📈 주식 조건 검색 및 추천 모바일 앱 (Stock Signal App)

## 🧩 프로젝트 소개

한국 주식 데이터를 수집하고 관리자가 등록한 검색식을 기반으로 종목을 분석하여
Signal을 생성하는 서비스입니다. Push 인프라는 구축되어 있으며 현재 SearchCondition 기반
Signal 저장 경로와의 연계는 후속 작업입니다.

단순 시세 조회 앱이 아니라 다음 흐름의 실서비스 MVP를 목표로 합니다.

```text
주식 데이터 수집
→ 조건식 분석
→ Signal 생성
→ 사용자 Push 알림
```

관리자가 검색식을 직접 등록하고 관리하며, 해당 검색식의 SCREENING/SIGNAL Rule을
실제 전체시장 및 실시간 체결 평가에 사용하는 **DB 기반 검색식 실행 구조**를 구축했습니다.

현재 Signal 처리 구조는 다음과 같습니다.

```text
관리자 Web
→ 검색식 등록
→ DB 저장
→ 전체 종목 Screening
→ 후보 종목 선정
→ realtimeEnabled 후보의 KIS WebSocket 실시간 감시
→ Signal 생성
```

---

## 🎯 주요 기능

### 🔐 인증

* 회원가입 / 로그인
* JWT 인증
* 현재 사용자 조회 (`/api/auth/me`)
* 전화번호 AES 암호화 저장
* 사용자 권한

  * `USER`
  * `ADMIN`

### 🛡 관리자 권한

`/api/admin/**` API는 `ADMIN` 권한 사용자만 접근할 수 있습니다.

```text
USER
→ /api/admin/**
→ 403 Forbidden

ADMIN
→ /api/admin/**
→ 접근 허용
```

---

## 📊 주식 데이터

* 종목 검색 API
* 종목 상세 조회 API
* 현재가 저장 API
* 최신 현재가 조회 API
* KIND 상장법인 목록 Import
* KOSPI / KOSDAQ / KONEX 지원

전체 국내 종목 약 2,764개를 대상으로 현재가 데이터를 수집할 수 있는 구조를 사용합니다.

---

## ⭐ 관심종목

* 관심종목 추가
* 관심종목 삭제
* 관심종목 목록 조회

---

## 🔗 한국투자증권 KIS 연동

* 한국투자증권 Open API 연동
* Access Token 발급
* Access Token Redis 캐싱
* REST 현재가 조회
* KIS WebSocket Approval Key 발급
* WebSocket 연결
* 실시간 체결 데이터 수신
* 한 WebSocket Session에서 다중 종목 구독

### WebSocket 구독 테스트 결과

실제 테스트 결과 한 WebSocket Session에서:

```text
1 ~ 40종목
→ SUBSCRIBE SUCCESS

41번째 종목부터
→ OPSP0008
→ MAX SUBSCRIBE OVER
```

따라서 현재 프로젝트의 실제 테스트 및 운영 기준은:

```text
WebSocket 1 Session
→ 최대 40종목
```

입니다.

전체 감시 대상은 다음과 같이 여러 세션으로 나누어 구독합니다.

```text
전체시장 Screening
→ realtimeEnabled 후보 선정
→ 감시 대상을 40종목씩 partition
→ 여러 WebSocket Session 생성
→ 각 Session에서 최대 40종목 실시간 감시
```

40종목은 현재 프로젝트에서 실제 확인한 세션별 운영 기준이며, 전체 감시 대상을 앞의
40종목으로 제한한다는 의미가 아닙니다.

---

## ⏰ 자동 수집 Scheduler

* 장중 현재가 자동 수집
* 전체 종목 순차 수집
* 종목별 현재가 DB 저장
* KIS API 호출 제한 대응

수집 흐름:

```text
StockPriceScheduler
→ KIS REST API
→ 현재가 조회
→ stock_prices 저장
```

이 Scheduler는 장중 현재가 스냅샷을 `stock_prices`에 보조 수집하는 경로입니다.

---

# 📈 Signal 분석

Signal 조건은 Java 코드에 고정하지 않고 관리자가 등록한 SearchCondition의 SIGNAL Rule로
평가합니다. 실시간 체결값과 필요한 일봉 이력을 결합해 검색식별 일치 여부를 계산합니다.

## 중복 Signal 방지

```text
동일 종목
+ 동일 SearchCondition
+ 최근 30분
→ 중복 생성 방지
```

Signal은 특정 종목이 특정 관리자 SearchCondition의 SIGNAL 조건에 언제 일치했는지를
기록하는 이벤트입니다.

---

# ⚡ 실시간 Signal 처리

KIS WebSocket 실시간 체결 데이터를 Signal 분석 계층으로 전달합니다.

현재 흐름:

```text
KIS WebSocket
↓
KisWebSocketHandler
↓
실시간 체결 데이터 파싱
↓
RealtimeWatchTargetRegistry에서 종목별 SearchCondition 조회
↓
SearchCondition SIGNAL Rule 평가
↓
일치한 SearchCondition별 Signal 저장
```

전체시장 Screening 결과 중 `realtimeEnabled` 검색식에 일치한 후보만 감시 대상으로 구성하며,
실시간 평가와 저장은 `RealtimeSignalEvaluator`, `RealtimeSignalPersistenceService`가 담당합니다.

---

# 🔔 Push Notification

* Expo Push Token 저장
* Firebase Cloud Messaging V1
* Expo Push Service
* Android 실제 기기 Push 수신 확인 완료

Push 발송 인프라와 기기 수신은 검증했지만, 현재 SearchCondition 기반 Signal persistence와
Push 발송의 연결은 후속 작업입니다.

---

# 🔎 DB 기반 검색식 관리

관리자가 웹 관리자 페이지에서 검색식을 등록하고 실제 분석에 사용할 수 있도록
DB 기반 검색식 도메인과 실행 구조를 구현했습니다.

현재 구현된 검색식 관리 구조:

```text
SearchCondition
        │
        │ 1:N
        ▼
SearchConditionRule
```

검색식 하나에 여러 Rule을 등록할 수 있습니다.

예:

```text
검색식
거래량 급증 상승주

SCREENING
20일 평균 대비 거래량 비율 >= 1.5
AND
등락률 >= 3%

SIGNAL
20일 평균 대비 거래량 비율 >= 2.0
```

---

## 검색식 단계

### SCREENING

전체 종목을 대상으로 WebSocket 실시간 감시 후보를 선정하기 위한 1차 조건입니다.

```text
전체 약 2,764종목
→ SCREENING
→ Candidate
```

### SIGNAL

WebSocket 실시간 데이터를 이용해 최종 Signal 발생 여부를 검사하기 위한 조건입니다.

```text
Candidate
→ WebSocket
→ SIGNAL 조건
→ Signal
```

활성 SearchCondition의 SCREENING Rule을 KOSPI/KOSDAQ 전체 종목에 적용하고,
그중 `realtimeEnabled` 조건에 일치한 Candidate를 실시간 감시 대상으로 전환합니다.

---

# 🧱 검색식 지원 지표

현재 관리자가 검색식에서 사용할 수 있도록 정의된 지표:

```text
CURRENT_PRICE
CHANGE_RATE
VOLUME
AVERAGE_VOLUME
VOLUME_RATIO
MOVING_AVERAGE
```

표시 의미:

| Code             | 설명               | 기간 필요 |
| ---------------- | ---------------- | ----- |
| `CURRENT_PRICE`  | 현재가              | X     |
| `CHANGE_RATE`    | 등락률              | X     |
| `VOLUME`         | 현재 거래량           | X     |
| `AVERAGE_VOLUME` | 평균 거래량           | O     |
| `VOLUME_RATIO`   | 평균 거래량 대비 거래량 비율 | O     |
| `MOVING_AVERAGE` | 이동평균             | O     |

향후 RSI, MACD, 거래대금, 변동성 등의 지표를 확장할 수 있도록 Enum 기반으로 구성되어 있습니다.

---

# 🔢 검색식 연산자

현재 지원 연산자:

```text
GREATER_THAN
GREATER_THAN_OR_EQUAL
LESS_THAN
LESS_THAN_OR_EQUAL
EQUAL
```

관리자 화면 표시:

```text
초과
이상
미만
이하
같음
```

---

# 🔀 검색식 비교 대상

Rule의 오른쪽 비교 대상은 두 종류를 지원합니다.

### VALUE

고정값 비교:

```text
CHANGE_RATE >= 3
```

### METRIC

지표 간 비교:

```text
CURRENT_PRICE > MOVING_AVERAGE(5)
```

이를 통해 단순 숫자 조건과 지표 간 조건을 모두 표현할 수 있습니다.

---

# 🔗 검색식 논리 연산자

지원 논리 연산자:

```text
AND
OR
```

현재 MVP에서는 단순 Rule 연결을 지원합니다.

향후 필요 시:

```text
A AND (B OR C)
```

형태의 그룹 조건으로 확장할 예정입니다.

---

# 🛠 관리자 검색식 API

Base URL:

```text
/api/admin/search-conditions
```

모든 API는 `ADMIN` 권한이 필요합니다.

### 검색식 목록 조회

```http
GET /api/admin/search-conditions
```

### 검색식 상세 조회

```http
GET /api/admin/search-conditions/{id}
```

### 검색식 등록

```http
POST /api/admin/search-conditions
```

### 검색식 수정

```http
PUT /api/admin/search-conditions/{id}
```

### 검색식 활성 / 비활성

```http
PATCH /api/admin/search-conditions/{id}/enabled
```

검색식은 기본적으로 물리 삭제보다 활성/비활성 방식으로 관리합니다.

---

# 🧩 검색식 메타데이터 API

관리자 Web에서 Enum 값을 직접 하드코딩하지 않도록 Backend에서 지원 가능한 검색식 구성 정보를 제공합니다.

```http
GET /api/admin/search-conditions/meta
```

응답 정보:

```text
metrics
operators
rightTypes
logicalOperators
stages
```

예:

```json
{
  "metrics": [
    {
      "code": "CURRENT_PRICE",
      "label": "현재가",
      "periodRequired": false
    },
    {
      "code": "MOVING_AVERAGE",
      "label": "이동평균",
      "periodRequired": true
    }
  ]
}
```

향후 Backend에서 새로운 지표를 추가하면 관리자 Web이 메타데이터 API를 이용해 선택 항목을 구성할 수 있습니다.

---

# 🛠 기술 스택

## Backend

* Java 21
* Spring Boot 3.5.x
* Spring Security
* JWT
* Spring Data JPA
* Hibernate
* Spring Scheduler
* Gradle

## Database

* MySQL 8
* Redis

## Mobile

* React Native
* Expo
* AsyncStorage
* Axios

## Push

* Firebase Cloud Messaging V1
* Expo Push Service

## External

* 한국투자증권 Open API
* 한국투자증권 WebSocket

---

# 📂 프로젝트 구조

```text
com.stockapp
 ├─ domain
 │   ├─ user
 │   ├─ stock
 │   ├─ signal
 │   ├─ screening
 │   │   └─ dto
 │   ├─ favorite
 │   └─ notification
 │
 ├─ global
 │   ├─ security
 │   ├─ config
 │   ├─ error
 │   └─ util
 │
 └─ external
     ├─ kis
     └─ firebase
```

---

# ⚙️ 주요 구현 포인트

## 1. KIS API

```text
Access Token 발급
→ Redis 캐싱
→ REST 현재가 조회
```

WebSocket:

```text
Approval Key 발급
→ WebSocket 연결
→ 종목 SUBSCRIBE
→ 실시간 체결 수신
```

---

## 2. 현재가 데이터

```text
KIS
→ 현재가 조회
→ stock_prices 저장
→ 최신 데이터 조회
```

---

## 3. 실시간 Signal

```text
관리자 SearchCondition
→ KOSPI/KOSDAQ 전체시장 SCREENING Rule 평가
→ realtimeEnabled Candidate
→ RealtimeWatchTargetRegistry
→ 40종목 단위 KIS WebSocket 다중 세션 구독
→ 실시간 체결 수신
→ SearchCondition SIGNAL Rule 평가
→ 일치한 SearchCondition별 Signal 저장
```

---

## 4. DB 검색식

```text
관리자
→ SearchCondition 등록
→ SearchConditionRule 저장
```

```text
DB 활성 검색식
→ Screening Engine
→ 전체 종목 검사
→ Candidate
→ Priority
→ realtimeEnabled 감시 대상
→ 40종목 단위 WebSocket 다중 세션
```

---

# 📡 주요 API

## 인증

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

## Signal

```http
GET /api/signals
```

최근 Signal을 발생 시각 내림차순으로 최대 50건 조회합니다. 인증이 필요합니다.

```json
{
  "id": 100,
  "stockCode": "005930",
  "stockName": "삼성전자",
  "searchConditionId": 3,
  "searchConditionName": "거래량 증가 + 이동평균 돌파",
  "message": "검색식 SIGNAL 조건 일치",
  "detectedAt": "2026-08-19T10:30:00"
}
```

## 현재가

```http
GET /api/stocks/{stockCode}/price/latest
```

## 관심종목

```http
GET    /api/favorites
POST   /api/favorites/{stockCode}
DELETE /api/favorites/{stockCode}
```

## 관리자 검색식

```http
GET   /api/admin/search-conditions
GET   /api/admin/search-conditions/meta
GET   /api/admin/search-conditions/{id}
POST  /api/admin/search-conditions
PUT   /api/admin/search-conditions/{id}
PATCH /api/admin/search-conditions/{id}/enabled
```

---

# ✅ MySQL Schema 검증

`schema-validate` profile은 Entity와 실제 local MySQL schema의 일치 여부만 검증합니다.

* `ddl-auto=validate`
* non-web 실행
* WebSocket startup 및 Scheduler 차단
* screening/daily runner 차단
* 검증 완료 후 context 종료

Windows PowerShell:

```powershell
.\gradlew.bat build --no-daemon -x test

java -jar build/libs/stock-signal-backend-0.0.1-SNAPSHOT.jar `
  --spring.profiles.active=local,schema-validate
```

---

# ✅ 현재 구현 완료

* 회원가입
* 로그인
* JWT 인증
* `/me`
* USER / ADMIN 권한
* `/api/admin/**` ADMIN 접근 제어
* 전화번호 AES 암호화
* 종목 검색
* 종목 상세 조회
* 관심종목 등록 / 삭제 / 조회
* KIS REST 현재가 조회
* Redis Access Token 캐싱
* 현재가 DB 저장
* 전체 종목 보조 수집 Scheduler
* 관리자 SearchCondition SCREENING/SIGNAL Rule 평가
* KOSPI/KOSDAQ 전체시장 Screening
* realtimeEnabled Candidate 기반 실시간 감시 대상 구성
* 종목 + SearchCondition 기준 30분 Signal 중복 방지
* KIS WebSocket Approval Key
* KIS WebSocket 연결
* 감시 대상을 40종목씩 partition하는 다중 WebSocket Session 관리
* 세션별 WebSocket 40종목 운영 기준 확인
* 실시간 체결 수신
* SearchCondition별 실시간 Signal 평가 및 저장
* Android 실제 Push 수신
* 검색식 DB 모델
* 검색식 Rule DB 모델
* 관리자 검색식 등록 API
* 관리자 검색식 목록 조회 API
* 관리자 검색식 상세 조회 API
* 관리자 검색식 수정 API
* 관리자 검색식 활성 / 비활성 API
* 검색식 메타데이터 조회 API

---

# 🚧 다음 개발 예정

다음 주요 개발 단계:

```text
SearchCondition 기반 Signal
↓
사용자별 알림 대상 결정
↓
Expo Push 발송 연계
```

장기 운영 시에는 추가로:

* `stock_prices` 최근 90일 보관 정책
* 오래된 데이터 자동 삭제
* 일 단위 데이터 집계
* DB 인덱스 최적화
* WebSocket 재연결 안정화
* WebSocket 구독 대상 동적 교체
* 서버 배포
* HTTPS / Domain
* Docker 기반 운영 환경

등을 적용할 예정입니다.
