# AI System Design

Date: 2026-04-21

## Goal

Upgrade the current AI tutor from a single OpenAI-style endpoint with in-memory chat state into a reusable AI subsystem that:

- supports multiple user-configured providers
- supports both OpenAI-compatible and Anthropic-compatible APIs
- persists chat sessions locally
- maintains lightweight long-term user memory across sessions
- keeps the UI simple for the first release

This design is scoped to the existing Android app and its current architecture: Hilt, Compose, Retrofit, DataStore, and Room.

## Current State

The current implementation has four key limitations:

- AI configuration is stored as a single global tuple: `apiBaseUrl`, `apiKey`, `aiModel`, `aiEnabled`
- `AIRepository` only sends OpenAI-compatible `/chat/completions` requests
- chat history exists only in `AITutorViewModel` memory and is lost when the screen is recreated
- there is no persistent user memory or provider abstraction

These limitations make it hard to support multiple vendors, continue previous conversations, or personalize responses.

## Product Scope

The first release will include:

- provider management for multiple saved providers
- provider type selection: `OPENAI_COMPATIBLE` or `ANTHROPIC_COMPATIBLE`
- one active provider at a time
- one default resumable chat session exposed in the UI
- local chat history persistence
- automatic long-term memory extraction from user messages
- long-term memory injection into future prompts

The first release will not include:

- user-editable memory management UI
- vector search or embedding retrieval
- streaming responses
- image, audio, tool-calling, or function-calling support
- arbitrary provider DSL or templated request builders
- multi-session UI, even though storage will support multiple sessions

## Success Criteria

- Users can save and switch between multiple providers without editing raw app storage
- Users can configure either OpenAI-compatible or Anthropic-compatible APIs
- Closing and reopening the AI tutor resumes the previous conversation
- The assistant remembers stable user facts across sessions
- API errors are mapped to actionable messages instead of opaque failures
- The storage and adapter layers are extensible enough for future provider types

## Recommended Approach

Use a layered architecture with Room-backed persistence and protocol-specific adapters.

Why this approach:

- it matches the existing app stack
- it isolates protocol differences away from Compose screens and ViewModels
- it allows persistent sessions and memories without overloading DataStore
- it gives a clean migration path from the current single-provider setup

Rejected alternatives:

- keeping everything in DataStore would be quick but would make sessions, querying, and migration brittle
- building a generic provider DSL would be flexible but would significantly increase UI, validation, and error-handling complexity for the first release

## Architecture

### Layers

1. UI layer
- `AITutorScreen`
- provider management UI in profile/settings
- future session UI hooks

2. ViewModel layer
- `AITutorViewModel`
- provider settings ViewModel logic

3. Domain/data orchestration layer
- `AIChatGateway`
- `ChatSessionRepository`
- `MemoryRepository`
- `MemoryExtractor`
- `ProviderRepository`

4. Protocol adapter layer
- `AiProviderAdapter`
- `OpenAiCompatibleAdapter`
- `AnthropicCompatibleAdapter`

5. Persistence layer
- Room entities, DAOs, mappers
- secure provider credential storage strategy

### Main Responsibilities

`AIChatGateway`
- loads active provider
- loads current session and recent messages
- loads long-term memory summary
- composes a normalized request context
- dispatches to the correct adapter
- persists assistant reply
- triggers memory extraction after successful completion

`ProviderRepository`
- manages provider CRUD
- tracks the active provider
- exposes provider list to settings UI

`ChatSessionRepository`
- creates or restores a default session
- persists messages
- loads recent messages for prompt composition

`MemoryRepository`
- stores long-term memory facts
- merges new facts into existing facts
- exposes a compact summary for prompt injection

`MemoryExtractor`
- extracts stable user facts from recent user messages only
- merges and deduplicates facts conservatively

## Data Model

### Provider Type

`AIProviderType`
- `OPENAI_COMPATIBLE`
- `ANTHROPIC_COMPATIBLE`

### Room Entities

`AiProviderEntity`
- `id: Long`
- `name: String`
- `type: AIProviderType`
- `baseUrl: String`
- `apiKeyEncrypted: String`
- `defaultModel: String`
- `customHeadersJson: String`
- `isActive: Boolean`
- `createdAt: Long`
- `updatedAt: Long`

`ChatSessionEntity`
- `id: Long`
- `title: String`
- `providerId: Long`
- `model: String`
- `createdAt: Long`
- `updatedAt: Long`

`ChatMessageEntity`
- `id: Long`
- `sessionId: Long`
- `role: String`
- `content: String`
- `createdAt: Long`

`UserMemoryEntity`
- `id: Long`
- `category: String`
- `content: String`
- `confidence: Float`
- `sourceSessionId: Long`
- `updatedAt: Long`

### Memory Categories

The first release will use a small fixed set:

- `profile`
- `preferences`
- `goals`
- `study_context`
- `constraints`

## API Normalization

### Internal Models

Introduce normalized internal models so UI and repositories are provider-agnostic:

- `NormalizedChatMessage(role, content)`
- `NormalizedChatRequest(model, messages, systemPrompt, memorySummary)`
- `NormalizedChatResponse(content, providerMessageId, finishReason)`

### Adapter Interface

`AiProviderAdapter`
- `supports(type: AIProviderType): Boolean`
- `send(config, request): NormalizedChatResponse`

### OpenAI-Compatible Behavior

Request target:
- append `/chat/completions` to the normalized base URL unless already present

Authentication:
- `Authorization: Bearer <apiKey>`

Body:
- use `model`
- include `messages` list with roles `system`, `user`, `assistant`

Response extraction:
- read first choice message content

### Anthropic-Compatible Behavior

Request target:
- append `/messages` to the normalized base URL unless already present

Authentication:
- `x-api-key: <apiKey>`
- required Anthropic version header, initially fixed in code and overridable later if needed

Body:
- use `model`
- send system prompt separately
- convert normalized messages to Anthropic message format
- exclude unsupported roles from direct pass-through as needed

Response extraction:
- concatenate text content blocks from the first response payload

### URL Handling Rules

Base URL handling must be forgiving:

- if user enters `/v1`, keep it
- if user enters a host root, append the protocol-specific path
- avoid double-appending paths

This prevents common misconfiguration when users paste provider roots from vendor docs.

## Prompt Composition

Each request will be composed from four parts:

1. fixed app system prompt
2. long-term memory summary
3. recent session history
4. latest user message

### System Prompt

A single internal system prompt will define the tutor role and constrain memory usage:

- the assistant is a study-oriented tutor
- memory is supplemental context, not unquestionable fact
- if memory seems stale or contradictory, the assistant should ask for clarification

### Memory Summary

The memory summary is a compact synthesized block generated from stored structured memories.
It should remain short and stable. It is not a raw dump of all memories.

### Recent Message Window

Only the latest bounded set of messages should be sent to the model.
The initial policy:

- include the most recent 12 to 20 messages, configurable in code
- always include the latest user message
- keep long-term memory separate from chat history

## Memory Extraction

### Extraction Policy

Memory extraction is conservative by design.

Extract only facts that are:

- stated by the user
- likely to matter in future conversations
- stable enough to survive beyond the current turn

Do not store:

- one-off emotional statements
- transient scheduling details unless clearly persistent
- speculative assistant inferences
- full conversation transcripts as memory

### Merge Rules

- deduplicate semantically identical facts
- prefer newer facts when they conflict with old facts
- prefer more specific facts over generic ones
- cap the number of memories per category

### First-Release Extraction Strategy

Use deterministic extraction rules plus lightweight summarization logic, not embeddings.

Implementation guidance:

- start with rule-based extraction from user messages
- optionally add local heuristic summarization over recent turns
- keep extraction fully local to avoid requiring a second model call

This keeps latency and cost predictable.

## Persistence and Migration

### Storage Choice

Move session and memory persistence to Room.

Keep DataStore only for lightweight flags or migration remnants, not for structured chat history.

### Migration from Current AI Settings

On first app launch after upgrade:

- read legacy `aiEnabled`, `apiBaseUrl`, `apiKey`, `aiModel`
- if an API key exists, create a default provider entry
- set that provider active
- preserve the current enabled flag semantics for the UI

This avoids breaking existing users who already configured AI access.

### API Key Handling

Provider credentials should not remain as plain Room text.

Initial design:

- encrypt API keys before persistence
- use the app’s existing Android security facilities where possible

If secure key storage cannot be completed in the same implementation slice, use a clearly isolated credential storage abstraction so the plain-text fallback is temporary and easy to replace.

## UI Design

### Provider Management

Replace the current single global AI config form with provider management:

- list saved providers
- show active provider
- add provider
- edit provider
- activate provider
- delete provider

Provider form fields:

- provider name
- provider type
- base URL
- API key
- default model
- optional custom headers advanced section

### AI Tutor Screen

The AI tutor screen will:

- show current active provider and model in the header or status area
- restore the last active session on entry
- allow continuing the chat seamlessly
- still support clearing the visible conversation

Clear behavior for the first release:

- clearing chat resets the current session messages
- long-term memory is not cleared by this action

This distinction must be explicit in the UI text.

## Error Handling

Map low-level failures into a small set of user-facing categories:

- missing configuration
- invalid API key
- provider URL/path mismatch
- unsupported response format
- model not found
- rate limit / quota exceeded
- network timeout / connectivity issue

Examples:

- OpenAI-compatible 401 becomes “API Key 无效或已失效”
- Anthropic-compatible malformed path becomes “接口地址与所选协议不匹配”
- empty assistant payload becomes “模型返回了空响应”

## Testing

Required tests for the first release:

- provider repository CRUD tests
- migration test from legacy single-provider settings
- OpenAI-compatible request composition test
- Anthropic-compatible request composition test
- URL normalization tests
- error mapping tests
- session persistence tests
- memory extraction and merge tests
- `AITutorViewModel` restore-session behavior test

Manual verification scenarios:

- configure one OpenAI-compatible provider and chat
- configure one Anthropic-compatible provider and chat
- switch active provider and confirm the screen reflects the active provider
- kill and reopen app, verify session restore
- confirm long-term memory affects later conversations

## Implementation Order

1. Add Room schema and repositories for providers, sessions, messages, and memories
2. Add migration from legacy AI settings
3. Introduce normalized chat models and adapter interface
4. Implement OpenAI-compatible adapter
5. Implement Anthropic-compatible adapter
6. Build `AIChatGateway`
7. Update `AITutorViewModel` to use persistent sessions
8. Replace current AI settings UI with provider management UI
9. Add memory extraction and summary injection
10. Add tests and manual verification

## Risks and Tradeoffs

- Anthropic-compatible providers vary more than OpenAI-compatible providers, so response parsing and required headers must be implemented defensively
- long-term memory can become stale or intrusive if extraction is too aggressive
- secure API key storage adds implementation complexity but should not be skipped casually
- supporting multiple sessions in storage while exposing a single-session UI is intentional; it reduces future refactor cost without increasing first-release UX complexity much

## Final Recommendation

Implement a Room-backed AI subsystem with two protocol adapters: OpenAI-compatible and Anthropic-compatible.

Persist chat history locally, extract conservative long-term memory from user messages, and inject only a compact memory summary plus recent conversation into prompts.

Keep the first UI release focused:

- multiple saved providers
- one active provider
- one resumable chat session
- no memory editor yet

This delivers a meaningful upgrade in capability without overdesigning the provider system.
