# 📈 주식 조건 검색 및 추천 모바일 앱 (Stock Signal App)

## 🧩 프로젝트 소개

한국 주식 데이터를 수집하고 조건식을 기반으로 종목을 분석하여
Signal을 생성하고 사용자에게 Push 알림을 제공하는 서비스입니다.

단순 시세 조회 앱이 아니라 다음 흐름의 실서비스 MVP를 목표로 합니다.

```text
주식 데이터 수집
→ 조건식 분석
→ Signal 생성
→ 사용자 Push 알림
```

현재 기존 Signal 조건 분석 기능에 더해, 관리자가 검색식을 직접 등록하고 관리할 수 있도록 **DB 기반 검색식 관리 구조**를 구축하고 있습니다.

최종 목표 구조는 다음과 같습니다.

```text
관리자 Web
→ 검색식 등록
→ DB 저장
→ 전체 종목 Screening
→ 후보 종목 선정
→ KIS WebSocket 실시간 감시
→ Signal 생성
→ Push
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

따라서 현재 확인된 구독 제한은:

```text
WebSocket 1 Session
→ 최대 40종목
```

입니다.

전체 종목을 WebSocket으로 직접 감시하는 대신 향후:

```text
전체 종목
→ REST Screening
→ 후보 선정
→ 상위 40종목
→ WebSocket 실시간 감시
```

구조로 확장할 예정입니다.

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

REST 데이터는 향후 전체 시장의 1차 Screening 데이터로 활용할 예정입니다.

---

# 📈 Signal 분석

현재 Signal 분석은 기존 Java 코드 기반 조건으로 동작하고 있습니다.

## 거래량 급증

조건:

```text
현재 거래량 >= 최근 평균 거래량 × 2
```

## 이동평균 돌파

현재 구현 조건:

```text
현재가 > 최근 가격 데이터 평균
```

## 중복 Signal 방지

```text
동일 종목
+ 동일 SignalType
+ 최근 30분
→ 중복 생성 방지
```

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
RealtimeSignalService
↓
조건 분석
↓
Signal 생성
↓
Push
```

실제 Android 기기에서 Signal 발생 후 Push 수신까지 확인되었습니다.

---

# 🔔 Push Notification

* Expo Push Token 저장
* Firebase Cloud Messaging V1
* Expo Push Service
* Signal 발생 시 Push 연계
* Android 실제 기기 Push 수신 확인 완료

---

# 🔎 DB 기반 검색식 관리

기존 Signal 조건은 Java 코드에 하드코딩되어 있습니다.

이를 관리자가 웹 관리자 페이지에서 검색식을 등록할 수 있는 구조로 변경하기 위해 DB 기반 검색식 도메인을 추가했습니다.

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
→ Push
```

현재는 **검색식 DB 등록 및 관리 기능까지만 구현되어 있으며**, DB 검색식을 실제 전체 종목 분석에 사용하는 Screening Engine은 향후 단계에서 구현합니다.

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
KIS WebSocket
→ KisWebSocketHandler
→ RealtimeSignalService
→ Signal
→ Push
```

---

## 4. DB 검색식

```text
관리자
→ SearchCondition 등록
→ SearchConditionRule 저장
```

향후:

```text
DB 활성 검색식
→ Screening Engine
→ 전체 종목 검사
→ Candidate
→ Priority
→ Top 40
→ WebSocket
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
* 거래량 급증 Signal
* 이동평균 돌파 Signal
* Signal 중복 방지
* KIS WebSocket Approval Key
* KIS WebSocket 연결
* 단일 Session 다중 종목 구독
* WebSocket 40종목 제한 확인
* 실시간 체결 수신
* RealtimeSignalService 연동
* Signal → Push 연동
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

현재 검색식 관리 기능은 DB 등록 및 CRUD까지 구현되어 있습니다.

다음 주요 개발 단계:

```text
관리자 Web 프로젝트
↓
검색식 관리 화면
↓
DB 검색식 실행 Screening Engine
↓
전체 종목 1차 Screening
↓
Candidate Priority 계산
↓
WebSocket 후보 Top 40 선정
↓
WebSocket 구독 종목 동적 교체
```

장기 운영 시에는 추가로:

* `stock_prices` 최근 90일 보관 정책
* 오래된 데이터 자동 삭제
* 일 단위 데이터 집계
* DB 인덱스 최적화
* WebSocket 재연결 안정화
* 서버 배포
* HTTPS / Domain
* Docker 기반 운영 환경

등을 적용할 예정입니다.
