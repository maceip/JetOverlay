# Phase 07: Real LLM Integration (Pure LiteRT-LM)

This phase replaces the `StubLlmService` with a real on-device LLM implementation using the **LiteRT-LM Kotlin API**. This implementation supports stateful multi-session conversations and native tool calling, drawing architecture patterns from the `koog` and `EveryCom` projects.

## Objectives

- [x] Replace `StubLlmService` with `LiteRTLlmService`.
- [x] Integrate `com.google.ai.edge.litertlm:litertlm-android` library.
- [x] Implement a `ToolBridge` for native tool calling with `@Tool` support.
- [x] Manage independent stateful conversations (one per intercepted message).

## Architecture & Implementation

### 1. Multi-Session Logic
The `LiteRTClient` now maintains a `ConcurrentHashMap` of `Conversation` objects, keyed by a unique session ID (the `message.id`). This ensures that if the app intercepts 3 WhatsApp messages, each one receives its own isolated context.

### 2. Tool Calling Bridge
The `AppToolBridge` class implements the `ToolRegistry` interface. It defines standard tools like `get_current_time`, `calculate_sum`, and `mark_as_read`. These are registered with the model through the `ConversationConfig`.

### 3. Interface Alignment
Mapped `LlmService` capabilities to match the `EveryCom` `LlmProvider` pattern, adding support for structured analysis and session closure.

## Tasks

### 1. Dependency Management
- [x] Add `libs.litertlm.android` to version catalog and build file.

### 2. Implementation
- [x] Create `LiteRTClient` with multi-session support (`ConcurrentHashMap`).
- [x] Create `AppToolBridge` with `@Tool` and `@ToolParam` annotations.
- [x] Create `LiteRTLlmService` to orchestrate sessions and prompt engineering.
- [x] Create `ModelManager` to resolve `.litertlm` file paths.

### 3. Integration & Testing
- [x] Update `MessageProcessor` to use `LiteRTLlmService` and call `closeSession()`.
- [x] Create `MultiSessionLlmTest.kt` to verify isolation for multiple messages.
- [x] Create `scripts/push_model.ps1` for model deployment.

## Conclusion
The "Brain" layer is now technically complete. it supports the full LiteRT-LM feature set, manages complex session state across multiple concurrent messages, and provides a framework for tool expansion.
