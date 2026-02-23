# 🔮 Recommendation Service

> **AI 모델을 기반으로 승객 수요를 예측하고 기사에게 최적의 대기 장소를 추천합니다.**

## 🛠 Tech Stack
| Category | Technology                                |
| :--- |:------------------------------------------|
| **Language** | **Java 17** |
| **Framework** | Spring Boot (WebFlux)                     |
| **External** | GCP Vertex AI, Naver Maps                 |
| **Database** | Redis (Reactive, Geo-spatial)             |
| **Resilience** | Resilience4j (CircuitBreaker, TimeLimiter)|

## 📡 API Specification

| Method | URI | Description |
| :--- | :--- | :--- |
| `GET` | `/api/recommendations` | 현재 위치(경도, 위도) 기반 최적 대기 장소 예측 및 추천 |

## 🚀 Key Improvements

* **Fully Reactive Pipeline (Non-blocking I/O):** Redis Geo 공간 검색부터 Vertex AI 예측, Naver Maps 역지오코딩까지 이어지는 일련의 과정을 `WebClient`와 `WebFlux(Mono/Flux)`를 사용하여 비동기 논블로킹 파이프라인으로 구축해 리소스 효율을 극대화했습니다.
* **Redis Geo-Spatial Query:** Redis의 GEO 명령어(`radius`)를 활용하여 기사의 현재 위치를 중심으로 반경 7km 이내의 수요 핫스팟 후보군을 실시간으로 빠르게 필터링합니다.
* **Resilience4j 기반 Fault Tolerance (장애 격리):**
  * 외부 API(GCP, Naver) 통신 구간에 **Circuit Breaker**를 적용하여 장애 전파를 차단했습니다.
  * API 특성에 맞춰 **TimeLimiter**를 차등 적용했습니다 (Vertex AI: 5초, Naver Maps: 3초). 타임아웃 발생 시 자동으로 요청을 취소(`cancel-running-future: true`)하여 불필요한 스레드 점유를 방지합니다.
* **안전한 Fallback(대체) 로직:** AI 예측이나 역지오코딩 API 호출 실패 또는 서킷 오픈 시, 각각 기본값(`0.0`)과 대체 문자열(`"주소 확인 불가"`)을 반환하는 Fallback을 구현하여 시스템의 전체적인 흐름이 끊기지 않도록 설계했습니다.



----------

## 아키텍쳐
<img width="2324" height="1686" alt="Image" src="https://github.com/user-attachments/assets/81a25ff9-ee02-4996-80d3-f9217c3b7750" />
