# 📈 주식 조건 검색 및 추천 모바일 앱 (Stock Signal App)

## 🧩 프로젝트 소개

한국 주식 데이터를 수집하고  
특정 조건(거래량 급증, 이동평균 돌파 등)을 기반으로  
유망 종목을 선별하여 사용자에게 추천하는 모바일 서비스입니다.

단순 시세 조회 앱이 아닌  
👉 **조건 분석 기반 추천 서비스**를 목표로 합니다.

---

## 🎯 주요 기능

### 🔐 인증
- 회원가입 / 로그인 (JWT 기반)
- 현재 사용자 조회 (/me)
- 전화번호 AES 암호화 저장

### 📊 주식 데이터
- 종목 검색 API
- 종목 상세 조회 API
- 현재가 저장 API
- 최신 현재가 조회 API

### 🔗 외부 API 연동
- 한국투자증권(KIS) Open API 연동
- 실시간 현재가 조회
- Access Token Redis 캐싱

---

## 🛠 기술 스택

### Backend
- Spring Boot 3
- Spring Security + JWT
- JPA (Hibernate)
- Redis
- Scheduler (예정)

### Database
- MySQL

### Mobile
- React Native (Expo)

### External
- 한국투자증권 Open API (KIS)
- Firebase FCM (예정)

---

## 📂 프로젝트 구조

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

---

## ⚙️ 주요 구현 포인트

### 1. KIS API 연동 구조
- Access Token 발급 → Redis 캐싱
- 만료 전 재사용으로 API 호출 최소화

### 2. 현재가 데이터 관리
- KIS → 현재가 조회
- DB(stock_prices)에 저장
- 최신 데이터 조회 API 제공

### 3. 보안 설계
- JWT 인증 기반 API 보호
- 전화번호 AES 암호화 저장

### 4. 환경 분리
- application-local.yml
- application-prod.yml
- 민감 정보 Git 제외

---

## 📡 API 예시

### 🔹 KIS 현재가 조회 → DB 저장

POST /api/stocks/prices/kis/{stockCode}
Authorization: Bearer {JWT}

예시:
POST /api/stocks/prices/kis/005930

---

### 🔹 KIS 응답 예시

{
  "rt_cd": "0",
  "msg1": "정상처리 되었습니다.",
  "output": {
    "stck_prpr": "284000",
    "prdy_ctrt": "1.79",
    "acml_vol": "35540134"
  }
}

---

## 🧪 트러블슈팅

### ❌ 403 Forbidden
- 원인: GET 요청 사용
- 해결: POST 요청으로 변경

### ❌ 포트 충돌 (8080)
- 기존 실행 프로세스 종료

---

## 🚀 현재 구현 상태

✔ Spring Boot 프로젝트 구성  
✔ MySQL / Redis 연동  
✔ JWT 인증 (회원가입 / 로그인 / /me)  
✔ KIS API 연동 (토큰 발급 + 현재가 조회)  
✔ 현재가 DB 저장 API 구현  
✔ 최신 현재가 조회 API  

---

## 🔜 향후 개발 계획

- Scheduler 기반 자동 수집
- 조건 계산 엔진
  - 거래량 급증
  - 이동평균 돌파
- Signal 도메인 설계
- 추천 종목 API
- React Native 앱 연동
- Firebase FCM 푸시 알림

---

## 💡 프로젝트 목적

- 단순 데이터 제공이 아닌  
👉 **조건 기반 투자 의사결정 지원 시스템 구축**

- 실무에서 사용하는 구조
  - API 서버
  - 캐싱 전략
  - 외부 API 연동
  - 보안 처리

을 모두 반영한 포트폴리오 프로젝트

---

## 🧑‍💻 개발자

- Backend / System Design
- Spring Boot 기반 API 설계 및 구현
