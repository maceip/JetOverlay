# Phase 06: LLM Stub Evolution & LiteRT Integration

This phase covers both the evolution of the LLM stub into a bucket-aware generator and the integration of a real, on-device Large Language Model using Google's LiteRT (formerly TensorFlow Lite) framework.

## Part 1: LLM Stub Evolution (Completed)

This sub-phase evolved the simple LLM stub into a more sophisticated, bucket-aware response generator. Instead of returning a generic "hello" for all messages, the system now generates contextually relevant smart replies based on the message category (Urgent, Work, Social, etc.).

### Tasks
- [x] **Evolve StubLlmService with bucket-aware responses**: Implemented contextual response generation for each bucket (URGENT, WORK, SOCIAL, etc.).
- [x] **Update unit tests**: Verified bucket-specific response content and 800ms simulated delay.
- [x] **Verify integration**: Confirmed `MessageProcessor` correctly applies bucket-to-response mapping.
- [x] **Manual verification**: Verified response chips in the overlay UI.

## Part 2: LiteRT Integration (In Progress/Ongoing)

This sub-phase replaces the stub with a real LLM using LiteRT, enabling privacy-first, offline intelligence.

### Roadmap & Status
- [x] **Integrate LiteRT dependencies**: Added `com.google.ai.edge.litert` and GenAI libraries.
- [x] **Model Management**: Created `ModelManager.kt` to handle model files (e.g., Gemma 2 2b).
- [x] **Inference Engine**: Ported `LlmInference` and `Llm` wrappers for token generation.
- [x] **Session Management**: Implemented `LiteRTClient` and `ToolRegistry` to support future tool calling.
- [x] **Switch Brain**: Transitioned `MessageProcessor` to prefer `LiteRtLlmService` when available.

### Implementation Notes
- The system uses the **MediaPipe GenAI Tasks** library for loading `.litertlm` bundles.
- `LiteRTClient` handles the interaction with the model and maintains conversation context.
- Future phases will activate Kotlin-based tool calling (Calendar, Timer, etc.).