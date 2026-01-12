Phase 06: LLM Stub Evolution (LiteRT Integration)
This phase replaces the "Stub" LLM with a real, on-device Large Language Model using Google's LiteRT (formerly TensorFlow Lite) framework. The goal is to replicate the implementation found in the google-ai-edge/gallery repository, enabling privacy-first, offline intelligence. We will move from returning static "hello" strings to generating context-aware responses using the Session API to maintain conversation history per notification thread. While tool usage is not yet active, the architecture will be scaffolded to support Kotlin-based tool calling in the future.

Tasks
[ ] Integrate LiteRT dependencies and model management:

Add Gradle dependencies for com.google.ai.edge.litert and GenAI libraries.

Create app/src/main/java/com/yazan/jetoverlay/domain/llm/ModelManager.kt:

Manage downloading of the .bin / .tflite model files (e.g., Gemma 2 2b or equivalent edge model) to local storage.

Implement a download progress UI in the SettingsScreen created in Phase 05.

Check for hardware acceleration support (GPU/NPU delegates).

Constraint: Ensure the app handles the large file size (1GB+) gracefully without blocking the main thread.

[ ] Port Inference Engine from google-ai-edge/gallery:

Create app/src/main/java/com/yazan/jetoverlay/domain/llm/inference/:

Port the LlmInference wrapper class that initializes the engine.

Port the Llm class that handles the token generation loop.

Ensure strict adherence to the reference repo's implementation to leverage their optimization for Android memory limits.

[ ] Implement Session Management (Conversation History):

Create app/src/main/java/com/yazan/jetoverlay/domain/llm/SessionManager.kt:

Maintain a Map<String, LlmSession> where keys are unique notification thread IDs (e.g., pkg_name + conversation_id).

Implement a Least Recently Used (LRU) eviction policy to prevent memory overflows from too many open sessions.

Update LlmService interface to support session-based generation:

suspend fun generateResponse(threadId: String, prompt: String): String

[ ] Scaffold Tool Calling Support:

Create app/src/main/java/com/yazan/jetoverlay/domain/llm/tools/ToolRegistry.kt:

Define the Tool interface exactly as per google-ai-edge specs.

Create a stubbed CalendarTool and TimerTool as placeholders.

Modify the inference loop to recognize "Tool Use" tokens, even if we simply log them for now without executing.

[ ] Switch Brain to Real LLM:

Update MessageProcessor to use the new LiteRtLlmService instead of StubLlmService.

Add a "System Prompt" to the initialization: "You are an anxiety-reducing assistant. Your goal is to summarize notifications calmly and suggest brief, polite responses."

Test on a physical device (Emulator may be too slow/incompatible with GPU delegates).