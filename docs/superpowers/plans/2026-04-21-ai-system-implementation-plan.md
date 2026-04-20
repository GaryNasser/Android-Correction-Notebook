# AI System Implementation Plan

Date: 2026-04-21
Related spec: `docs/superpowers/specs/2026-04-21-ai-system-design.md`

## Goal

Implement the approved AI subsystem upgrade in a sequence that keeps the app functional at each stage, minimizes migration risk, and preserves a clear rollback path.

## Delivery Strategy

Implement in vertical slices, not as one large refactor.

Each slice should leave the project in a compilable state and reduce the amount of legacy AI code still in use.

## Phase 1: Persistence Foundation

### Scope

- Add Room entities for providers, chat sessions, chat messages, and user memories
- Add DAOs and database wiring
- Add repository skeletons for providers, sessions, messages, and memories
- Keep existing AI settings flow untouched for now

### Tasks

1. Add entities:
- `AiProviderEntity`
- `ChatSessionEntity`
- `ChatMessageEntity`
- `UserMemoryEntity`

2. Add DAOs:
- `AiProviderDao`
- `ChatSessionDao`
- `ChatMessageDao`
- `UserMemoryDao`

3. Register new tables in the Room database and add any required migration/version bump

4. Add repository interfaces or concrete repositories:
- `ProviderRepository`
- `ChatSessionRepository`
- `MemoryRepository`

### Acceptance Criteria

- Project compiles
- Database opens successfully
- Repositories can insert and query basic records

### Risks

- Existing Room setup may need migration handling
- Provider credential storage abstraction should be introduced now, even if encryption is finished later in the same slice

## Phase 2: Legacy Settings Migration

### Scope

- Migrate current single-provider DataStore settings into the new provider table
- Preserve user configuration automatically

### Tasks

1. Add migration coordinator executed on AI module initialization or app startup

2. Read legacy values from `AISettingsManager`:
- `aiEnabled`
- `apiBaseUrl`
- `apiKey`
- `aiModel`

3. If legacy API key exists and provider table is empty:
- create a default provider
- mark it active
- use a clear default name such as `默认 OpenAI Provider`

4. Preserve the enabled/disabled behavior for UI presentation during the transition

### Acceptance Criteria

- Existing users keep their AI configuration after upgrade
- Fresh installs do not get a phantom provider record

### Risks

- Migration must be idempotent
- Re-running migration must not duplicate providers

## Phase 3: Internal Chat Normalization

### Scope

- Introduce provider-agnostic request and response models
- Stop exposing provider-specific models directly to higher layers

### Tasks

1. Add normalized models:
- `NormalizedChatMessage`
- `NormalizedChatRequest`
- `NormalizedChatResponse`

2. Add `AIProviderType`

3. Add provider config model used internally by repositories and adapters

4. Keep existing OpenAI request model temporarily for adapter implementation only

### Acceptance Criteria

- Higher layers no longer depend on OpenAI-only response classes
- New abstractions compile without changing the UI yet

## Phase 4: Protocol Adapter Layer

### Scope

- Add explicit adapters for OpenAI-compatible and Anthropic-compatible APIs
- Centralize protocol differences

### Tasks

1. Add `AiProviderAdapter` interface

2. Implement `OpenAiCompatibleAdapter`
- URL normalization
- bearer auth
- `/chat/completions` handling
- first-choice content extraction

3. Implement `AnthropicCompatibleAdapter`
- URL normalization
- `x-api-key` auth
- Anthropic version header
- `/messages` handling
- text block extraction

4. Add `AiErrorMapper`
- translate status codes and malformed payloads into user-facing errors

### Acceptance Criteria

- Both adapters can send normalized requests and return normalized responses
- Error messages are clearer than raw Retrofit exceptions

### Risks

- Anthropic-compatible vendors differ more in payload strictness
- Base URL normalization must avoid duplicate paths

## Phase 5: Chat Orchestration Gateway

### Scope

- Replace the current thin `AIRepository.sendMessage()` flow with a real orchestration layer

### Tasks

1. Add `AIChatGateway`

2. Flow inside gateway:
- load active provider
- restore or create current session
- load recent messages
- load memory summary
- build normalized request
- dispatch to correct adapter
- persist assistant reply

3. Keep `AIRepository` as a thin façade if useful for compatibility, or retire it if unnecessary

### Acceptance Criteria

- One API call path exists for all AI chats
- Session persistence and protocol dispatch happen outside the ViewModel

## Phase 6: Persistent Chat Sessions

### Scope

- Make AI tutor chat resumable across screen recreation and app restarts

### Tasks

1. Update `AITutorViewModel` to load current session on init

2. Replace in-memory-only `_messages` source with repository-backed state

3. Persist:
- user message before request
- assistant reply after success

4. Define clear clear-chat behavior:
- clear current session messages
- keep long-term memory intact

### Acceptance Criteria

- Reopening AI tutor restores the previous conversation
- Sending a message updates both UI and storage
- Clearing chat empties the session without deleting provider config

## Phase 7: Long-Term Memory

### Scope

- Add structured local memory extraction and prompt injection

### Tasks

1. Add `MemoryExtractor`

2. Implement conservative rule-based extraction from user messages only

3. Add merge logic:
- deduplication
- conflict replacement by recency/specificity
- category limits

4. Add memory summarization for prompt injection

5. Inject compact memory summary into the request composer

### Acceptance Criteria

- Stable user preferences and background facts survive across sessions
- Prompt size remains bounded
- No raw transcript dumping into memory storage

### Risks

- Over-aggressive extraction will create incorrect personalization
- Under-aggressive extraction will make memory feel ineffective

## Phase 8: Provider Management UI

### Scope

- Replace the single AI config form with provider management

### Tasks

1. Add provider list UI

2. Add provider create/edit form with:
- provider name
- provider type
- base URL
- API key
- default model

3. Add activate/deactivate behavior

4. Show active provider summary in profile/settings

5. Preserve existing AI enabled switch semantics or replace them with “has active provider” semantics if that results in simpler UX

### Acceptance Criteria

- Users can save multiple providers
- Users can switch active provider without editing raw fields each time
- Existing users see migrated config in the new UI

## Phase 9: AI Tutor UI Integration

### Scope

- Update AI tutor screen to reflect the new system state

### Tasks

1. Show active provider and model in the tutor screen

2. Show meaningful empty states:
- no active provider
- active provider missing API key
- unsupported/invalid endpoint response

3. Ensure error banners or inline messages use mapped error categories

4. Keep current chat UX simple and stable

### Acceptance Criteria

- The tutor screen clearly explains configuration problems
- Users can tell which provider/model is currently active

## Phase 10: Testing and Verification

### Automated Tests

- provider repository CRUD
- migration idempotency
- URL normalization
- OpenAI-compatible adapter request/response mapping
- Anthropic-compatible adapter request/response mapping
- error mapping
- session persistence
- memory extraction and merge behavior
- `AITutorViewModel` restore and clear behavior

### Manual Checks

1. Configure OpenAI-compatible provider and complete a chat round-trip
2. Configure Anthropic-compatible provider and complete a chat round-trip
3. Switch providers and verify new requests use the active provider
4. Restart app and verify session resume
5. Verify long-term memory affects later conversations
6. Verify clear-chat does not erase long-term memory

## Suggested PR Breakdown

To reduce risk, split implementation into these PRs or commits:

1. Database + repository groundwork
2. Legacy migration + provider model introduction
3. Adapter layer + gateway
4. Persistent sessions in AI tutor
5. Long-term memory
6. Provider management UI
7. Cleanup of legacy AI settings code

## Cleanup Targets

After the new system is working:

- retire legacy single-provider logic from `AISettingsManager`
- remove OpenAI-only assumptions from `AIRepository`
- remove duplicated provider config dialog logic between profile and AI tutor if both still exist

## Definition of Done

The feature is complete when:

- multiple providers can be saved locally
- one active provider can be selected
- both OpenAI-compatible and Anthropic-compatible APIs work
- chat sessions persist locally
- long-term memory is extracted and reused
- legacy config is migrated automatically
- the UI clearly surfaces provider and model state
- tests cover core protocol and persistence logic
