# LikeLion AI Chatbot API

OpenAI GPT-3.5-turbo 기반의 AI 챗봇 REST API 서버입니다.
대화 생성, 메시지 스트리밍, 대화 이력 관리 등의 기능을 제공합니다.

## Tech Stack

| 구분 | 기술 |
|------|------|
| Framework | Spring Boot 3.5.10, Java 21 |
| Database | PostgreSQL, Spring Data JPA |
| Cache | Redis |
| AI | OpenAI GPT-3.5-turbo (theokanning/openai-gpt3-java) |
| Streaming | SSE (Server-Sent Events), OkHttp |
| API Docs | Springdoc OpenAPI 2.8.6 (Swagger UI) |
| Build | Gradle |

## Project Structure

```
src/main/java/com/minje/chatbot/
├── config/          # Security(필터 등록, CORS), OpenAI 설정
├── controller/      # REST API 컨트롤러
├── dto/             # 요청/응답 DTO
├── entity/          # JPA 엔티티 (User, Conversation, Message)
├── exception/       # 글로벌 예외 처리
├── filter/          # 서블릿 필터 (API Key 인증, Rate Limiting)
├── repository/      # Spring Data JPA 리포지토리
├── service/         # 비즈니스 로직 (ChatService, OpenAIService)
└── util/            # API Key 검증 유틸리티
```

## ERD

```
Users (1) ──── (N) Conversations (1) ──── (N) Messages
  id                   id                      id
  api_key              user_id (FK)            conversation_id (FK)
  created_at           title                   role (user/assistant/system)
  updated_at           created_at              content
                       updated_at              created_at
```

## 인증 및 Rate Limiting

### API Key 인증
모든 API 요청에 `X-API-KEY` 헤더가 필요합니다. (Swagger UI 경로 제외)

```
X-API-KEY: sk-proj-your-api-key
```

- 키가 없거나 형식이 잘못된 경우 `401 Unauthorized` 반환
- 등록되지 않은 키인 경우 `401 Unauthorized` 반환

### Rate Limiting
Redis 기반 고정 윈도우 방식으로 API Key당 **분당 10회** 요청을 제한합니다.

- 초과 시 `429 Too Many Requests` 반환
- 응답 헤더에 `X-RateLimit-Limit`, `X-RateLimit-Remaining` 포함

## API Endpoints

Base Path: `/api/v1`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/chat/completions` | GPT에게 메시지 전송 (동기 응답) |
| `POST` | `/chat/completions/stream` | GPT에게 메시지 전송 (SSE 스트리밍 응답) |
| `GET` | `/conversations` | 대화 목록 조회 (페이징) |
| `GET` | `/conversations/{id}` | 특정 대화 상세 조회 |
| `DELETE` | `/conversations/{id}` | 대화 삭제 |

### Request / Response 예시

**채팅 요청** `POST /api/v1/chat/completions`
```json
{
  "content": "안녕하세요!",
  "conversationId": null,
  "title": "새 대화"
}
```

**응답**
```json
{
  "success": true,
  "data": {
    "conversationId": 1,
    "content": "안녕하세요! 무엇을 도와드릴까요?",
    "role": "assistant"
  }
}
```

## Getting Started

### Prerequisites

- Java 21
- PostgreSQL
- Redis

### 설정

`src/main/resources/application.yaml`에서 DB 및 OpenAI 설정을 수정합니다:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: your_username
    password: your_password

openai:
  api-key: your_openai_api_key
```

### 실행

```bash
./gradlew bootRun
```

### API 문서 확인

서버 실행 후 Swagger UI에서 API를 테스트할 수 있습니다:

```
http://localhost:8080/api/v1/swagger-ui.html
```

## Features

- **API Key 인증** - X-API-KEY 헤더 기반 인증 필터
- **Rate Limiting** - Redis 기반 API Key당 분당 10회 요청 제한
- **동기/스트리밍 응답** - 일반 응답과 SSE 실시간 스트리밍 모두 지원
- **대화 컨텍스트 유지** - 최근 10개 메시지를 포함하여 GPT에 전송
- **대화 제목 자동 생성** - 첫 메시지 기반으로 대화 제목 자동 설정
- **페이징 처리** - 대화 목록 페이징 및 정렬 지원
- **글로벌 예외 처리** - 일관된 에러 응답 형식
- **Swagger UI** - API 문서 자동 생성 및 API Key 인증 테스트 지원

## Git History

| Commit | Description |
|--------|-------------|
| `e164882` | feat: API Key 인증 필터, Redis Rate Limiting, Swagger 보안 설정 추가 |
| `a6fd311` | fix: README.md 인코딩을 UTF-8로 변환 |
| `a0585f1` | docs: README.md 작성 - 프로젝트 소개, 기술 스택, API 문서 등 |
| `fa2a65e` | fix: /conversations 엔드포인트 500 에러 수정 |
| `58e8705` | fix: ChatService 및 관련 클래스 호환성 문제 수정 |
| `4f01ae7` | initial setting |
| `68b0785` | first commit |
