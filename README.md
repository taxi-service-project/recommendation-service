# MSA 기반 Taxi 호출 플랫폼 - Recommendation Service

Taxi 호출 플랫폼의 **AI 기반 운행 위치 추천** 기능을 담당하는 마이크로서비스입니다. 기사의 현재 위치를 기반으로, **미래 수요 예측 AI 모델(Vertex AI)**과 **핫스팟 데이터**를 활용하여 이동 시 수요가 가장 높을 것으로 예상되는 최적의 위치를 추천합니다. Spring WebFlux 기반의 Reactive 스택으로 구현되었습니다. 

## 주요 기능 및 워크플로우

1.  **핫스팟 데이터 로딩:**
    * 애플리케이션 시작 시(`@PostConstruct`), `hotspots.csv` 파일에서 미리 정의된 수요 핫스팟(위도/경도) 목록을 읽어 **Redis Geospatial**에 로드합니다.
2.  **추천 요청 처리 (API Endpoint - `/api/recommendations`):**
    * `GET /?lon={lon}&lat={lat}`
    * 기사의 현재 좌표를 입력받아 추천 프로세스를 시작합니다.
3.  **주변 핫스팟 검색 (`RecommendationService`):**
    * **Redis Geospatial (`GEORADIUS`)**을 사용하여 현재 위치 반경 7km 내의 핫스팟 목록을 조회합니다.
4.  **미래 수요 예측 (`RecommendationService`):**
    * 조회된 각 핫스팟에 대해, 현재 시간/요일 정보와 함께 **Vertex AI Client**를 호출하여 해당 핫스팟의 미래 예상 수요 점수를 예측합니다.
    * Reactive Stream을 사용하여 여러 핫스팟에 대한 예측을 병렬/비동기적으로 처리합니다.
5.  **최적 핫스팟 선정 (`RecommendationService`):**
    * 예측된 수요 점수가 가장 높은 핫스팟을 선정합니다.
6.  **위치 → 주소 변환 (`RecommendationService`):**
    * 선정된 최적 핫스팟의 좌표(위도/경도)를 **Naver Maps Client**를 호출하여 사람이 읽을 수 있는 주소명으로 변환합니다.
7.  **최종 추천 메시지 생성:**
    * 변환된 주소명을 포함하여 사용자에게 전달할 최종 추천 메시지를 생성하여 반환합니다. (예: "약 15분뒤 XX동 인근의 수요가 가장 높을 것으로 예상됩니다...")

## 기술 스택 (Technology Stack)

* **Language & Framework:** Java, Spring Boot, **Spring WebFlux**
* **Geospatial Database:** **Spring Data Reactive Redis (Geospatial)**
* **Inter-service Communication:** **Spring WebClient**
* **AI Model Integration:** Google Cloud Vertex AI
* **External API:** Naver Maps Reverse Geocoding API
