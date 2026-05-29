# 📈 주식 조건 검색 및 추천 모바일 앱 (Stock Signal App)

## 🧩 프로젝트 소개

한국 주식 데이터를 수집하고
특정 조건을 기반으로
유망 종목을 선별하여 사용자에게 추천하는 모바일 서비스입니다.

단순 시세 조회 앱이 아닌
👉 **조건 분석 기반 추천 서비스**를 목표로 합니다.

---

## 🎯 주요 기능

### 🔐 인증

* 회원가입 / 로그인 (JWT 기반)
* 현재 사용자 조회 (/me)
* 전화번호 AES 암호화 저장

### 📊 주식 데이터

* 종목 검색 API
* 종목 상세 조회 API
* 현재가 저장 API
* 최신 현재가 조회 API

### 🔗 외부 API 연동

* 한국투자증권(KIS) Open API 연동
* 실시간 현재가 조회
* Access Token Redis 캐싱

### ⏰ 자동 수집 Scheduler

* 1분 주기 현재가 자동 수집
* 종목별 현재가 저장
* KIS API 호출 제한 대응

### 📈 Signal 분석 엔진

#### 거래량 급증 (Volume Spike)

조건

현재 거래량 >= 최근 평균 거래량 × 2

#### 이동평균 돌파 (Moving Average Breakout)

조건

현재가 > 최근 5개 데이터 평균가

### 🎯 추천 Signal API

* 최신 추천 종목 조회
* GET /api/signals

---

## 🛠 기술 스택

### Backend

* Spring Boot 3
* Spring Security + JWT
* JPA (Hibernate)
* Redis
* Spring Scheduler

### Database

* MySQL

### Mobile

* React Native (Expo)

### External

* 한국투자증권 Open API (KIS)
* Firebase FCM (예정)

---

## 📂 프로젝트 구조

```text
com.stockapp
 ├─ domain
 │   ├─ user
 │   ├─ stock
 │   ├─ signal
 │   ├─ favorite
 │   └─ notification
 ├─ global
 │   ├─ security
 │   ├─ config
 │   ├─ error
 │   └─ util
 └─ external
     ├─ kis
     └─ firebase
```

---

## ⚙️ 주요 구현 포인트

### 1. KIS API 연동 구조

* Access Token 발급 → Redis 캐싱
* 만료 전 재사용으로 API 호출 최소화

### 2. 현재가 데이터 관리

* KIS → 현재가 조회
* DB(stock_prices)에 저장
* 최신 데이터 조회 API 제공

### 3. 자동 수집 Scheduler

* Spring Scheduler 적용
* 1분 주기 현재가 자동 수집
* 종목별 순차 처리
* API 호출 제한 대응 (Thread.sleep)

수집 흐름

```text
Scheduler
→ KIS 현재가 조회
→ stock_prices 저장
```

### 4. Signal 분석 엔진

거래량 급증 분석

```text
최근 거래량 평균 계산
→ 현재 거래량 비교
→ Signal 생성
```

이동평균 돌파 분석

```text
최근 가격 평균 계산
→ 현재가 비교
→ Signal 생성
```

중복 생성 방지

```text
동일 종목
+ 동일 SignalType
+ 최근 30분
```

### 5. 보안 설계

* JWT 인증 기반 API 보호
* 전화번호 AES 암호화 저장

### 6. 환경 분리

* application-local.yml
* application-prod.yml
* 민감 정보 Git 제외

---

## 📡 API 예시

### 🔹 KIS 현재가 조회 → DB 저장

POST /api/stocks/prices/kis/{stockCode}

Authorization: Bearer {JWT}

예시

POST /api/stocks/prices/kis/005930

---

### 🔹 KIS 응답 예시

```json
{
  "rt_cd": "0",
  "msg1": "정상처리 되었습니다.",
  "output": {
    "stck_prpr": "284000",
    "prdy_ctrt": "1.79",
    "acml_vol": "35540134"
  }
}
```

---

### 🔹 추천 Signal 조회

GET /api/signals

---

### 🔹 Signal 응답 예시

```json
[
  {
    "id": 5,
    "stockCode": "247540",
    "stockName": "에코프로비엠",
    "signalType": "MOVING_AVERAGE_BREAKOUT",
    "message": "이동평균 돌파 신호 발생",
    "baseValue": 117200,
    "currentValue": 121500,
    "changeRate": 1.04,
    "changeRatePercent": 4.0,
    "detectedAt": "2026-05-28T00:19:13.850668"
  }
]
```

---

## 🚀 현재 구현 상태

✔ Spring Boot 프로젝트 구성
✔ MySQL / Redis 연동
✔ JWT 인증 (회원가입 / 로그인 / /me)
✔ 전화번호 AES 암호화 저장
✔ KIS API 연동 (토큰 발급 + 현재가 조회)
✔ 현재가 DB 저장 API 구현
✔ 최신 현재가 조회 API
✔ Scheduler 자동 수집
✔ 거래량 급증 Signal 분석
✔ 이동평균 돌파 Signal 분석
✔ Signal 중복 생성 방지
✔ 추천 Signal 조회 API

---

## 🔜 향후 개발 계획

* 관심 종목 기능
* Firebase FCM 푸시 알림
* React Native 앱 연동
* 관리자 페이지
* Signal 고도화

  * RSI
  * MACD
  * 볼린저 밴드
  * 골든크로스

---

## 🧑‍💻 개발자

* 이름: Y.YB
* GitHub: https://github.com/doltank777
