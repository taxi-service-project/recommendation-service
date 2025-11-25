# 🔮 Recommendation Service

> **AI 모델을 기반으로 승객 수요를 예측하고 기사 대기 장소를 추천합니다.**

## 🛠 Tech Stack
| Category | Technology                |
| :--- |:--------------------------|
| **Language** | **Java 17**               |
| **Framework** | Spring WebFlux            |
| **External** | GCP Vertex AI, Naver Maps |
| **Resilience** | Resilience4j              |

## 📡 API Specification

| Method | URI | Description |
| :--- | :--- | :--- |
| `GET` | `/api/recommendations` | 현재 위치 기반 최적 대기 장소 추천 |

## 🚀 Key Improvements
* **Fault Isolation:** 외부 API 호출 시 **Circuit Breaker**를 적용하여 장애 전파 차단.
* **Adaptive Timeouts:** 서비스별로 타임아웃 정책 차별화.
