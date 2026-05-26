# AGENTS.md

## Build Commands

```bash
# Build (skip tests)
mvn -DskipTests package

# Run tests
mvn -q -DskipTests=false test

# Build with release profile (includes GPG signing for deployment)
mvn -B package -Prelease
```

## Key Facts

- **Java**: 17 (temurin distribution)
- **Dependencies**: `com.gdxsoft.easyweb:emp-script-utils`, `emp-script`, `slf4j-api`
- **Testing**: JUnit Jupiter 5.7.1

## Architecture

- **Providers**: `com.gdxsoft.ai.providers.*` - qwen, openai, gemini, doubao, grok, tencent
- **Entry points**:
  - `Modes` - XML Mode/Step/Prompt/SqlQuery parsing with MD5 caching
  - `RequestDataFactory` / `RequestAIFactory` - provider-agnostic request building
  - `ChatManagerBase` - core chat management
- **SSE streaming**: `com.gdxsoft.ai.AiStreamOrPost`

## Important Patterns

- `Modes` caches parsed XML by MD5 hash - same content returns cached results
- Provider implementations follow OpenAI-compatible interfaces
- XML Mode schema supports `stream` and `actionSqlRef` attributes on `<step>`

## CI

- GitHub Actions workflow on release events (`maven-publish.yml`)
- Builds with `mvn -B package` on Ubuntu JDK 17

## Notes

- No pre-commit hooks configured
- Release profile requires GPG signing for Maven Central deployment
