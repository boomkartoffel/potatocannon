# 🥔 The Potato Cannon

The **Potato Cannon** is a lightweight, expressive HTTP testing library for Java and Kotlin applications, designed with a **Behavior-Driven Development (BDD)** mindset. 
While traditional BDD tools like Cucumber focus on high-level user stories, the Potato Cannon targets the technical behavior of APIs - status codes, headers, body content, and more.

---

## Why?

The purpose of testing is to have confidence about the correctness of ones' code and the behaviour of the application. 
The Potato Cannon enables **robust, black-box API testing** that validates feature-level expectations while allowing full freedom in internal refactoring.
Unlike unit tests that focus on implementation details, the Potato Cannon tests the **contract** of your APIs, ensuring they behave as expected regardless of how they are implemented.

This makes it ideal for teams that:

- Value **feature contracts** over implementation details
- Want to **test APIs independently** of the underlying code
- Aim to **decouple verification from architecture**
- Try to maintain **stable test suites** that can evolve with the codebase and don't break with every refactor
- Wish to **prototype and iterate** on the application without being blocked by rigid test structures
- Need to identify **regressions** in API behavior quickly and reliably

The Potato Cannon allows teams to define what the system **should do**, not how it does it.

---

## What?

- **Potatoes**: Define individual HTTP requests and their expected behavior
- **Cannons**: Fire one or more potatoes, optionally settings that apply to all requests
- **Fire Modes**: Support for sequential and parallel execution
- **Verifications**: Lambda-based assertions that are performed on request results
- **Request/Response Support**: Works with text or binary bodies
- **Fine tune HTTP**: High control over request construction with a simple API
- **Logging**: Clear, structured request/response logs with adjustable verbosity to easily debug issues
- **Java Interop**: Fully usable from Java, not just Kotlin

---

This framework is built to test **what your APIs deliver**, not how they are built - enabling stable, expressive test suites that support continuous refactoring and iteration.


## How to install

Add Potato Cannon as a **test dependency** in your project.

### Maven

```xml
<dependency>
  <groupId>io.github.boomkartoffel</groupId>
  <artifactId>potato-cannon</artifactId>
  <version>0.1.0-alpha</version>
  <scope>test</scope>
</dependency>
```

### Gradle

```gradle
testImplementation("io.github.boomkartoffel:potato-cannon:0.1.0-alpha")
```

## How to use

```kotlin
val cannon = baseCannon
    .addSettings(
      BasicAuth("user", "pass")
    )

val potato = Potato(
    method = HttpMethod.POST,
    path = "/test",
    body = TextBody("{ \"message\": \"hi\" }"),
    settings = listOf(
      ContentType.JSON,
      Expectation("Status Code is 200 and return value is Hello") { result ->
        assertEquals(200, result.statusCode)
        assertEquals("Hello", result.responseText())
      })
)

cannon.fire(potato)
```

### What this does

This example sends a real HTTP `POST` request to `/test` with a JSON body and verifies the API's behavior:

- The `Cannon` defines the base URL and uses basic authentication.
- The `Potato` describes the HTTP request: method, path, body, and expected headers.
- The response is verified for:
    - Status code `200`
    - Response body equal to `"Hello"`

### Why this matters

- Confirms the API is reachable, accepts authenticated requests, and behaves as expected.
- Detects breaking changes: if the endpoint path, required headers, auth, or response change, the test will fail.
- Ensures reliable contract-level testing across refactors.
- Makes it easy to test deployed services with minimal setup, no mocking, and full end-to-end validation.


### How this improves your test code

The Potato Cannon instance can be reused across multiple tests, allowing you to define the base URL and common strategies like authentication once. 
Furthermore, you can reuse verifications by defining them once on a global level, which can be applied to multiple potatoes.

```kotlin
private val is200Expectation = Expectation("Response is 200 OK") { result ->
            result.statusCode shouldBe 200
            }
```
This allows you to rigidly define expectations without bloating each test with repetitive code.

The output of the Potato Cannon is structured and clear, making it easy to understand what was tested and what the results were.

```
════════════════════  🥔  Potato Dispatch Log 🥔  ════════════════════
| ⏩ Request
|    Method:     POST
|    Path:       /test
|    Full URL:   http://127.0.0.1:53097/test
|    Headers:
|      authorization: Basic dXNlcjpwYXNz
|      content-type: application/json
|    Body:
|      {
|        "message" : "hi"
|      }
| 
| ⏪ Response
|    Status:  200
|    Time:    3ms
|    Headers:
|      content-length: 5
|      content-type: text/plain; charset=UTF-8
|    Body:
|      Hello
| 
| 🔍 Expectations (1):
|      ✔ Status Code is 200 and return value is Hello


```

## Versioning

Potato Cannon follows [Semantic Versioning](https://semver.org/).

See [CHANGELOG.md](./CHANGELOG.md) for details on past releases.


### Project Status

The Potato Cannon is currently in active development and considered **alpha**.

- Expect breaking changes while we refine the API.
- Feedback, suggestions, and contributions are very welcome!
- If you're using it and hit a limitation or have ideas to improve it, feel free to open an issue or discussion.

While we're working toward a more stable release, please note that method names, behavior, and structure may change as the project evolves.








