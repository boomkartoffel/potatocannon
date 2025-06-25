# 🥔 The Potato Cannon

The **Potato Cannon** is a lightweight, expressive HTTP testing library for Java and Kotlin application, designed with a **Behavior-Driven Development (BDD)** mindset. 
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
- **Cannons**: Fire one or more potatoes, optionally setting configuration that apply to all requests
- **Fire Modes**: Support for sequential and parallel execution
- **Verifications**: Lambda-based assertions that are performed on request results
- **Request/Response Support**: Works with text or binary bodies
- **Fine tune HTTP**: High control over request construction with a simple API
- **Logging**: Clear, structured request/response logs with adjustable verbosity to easily debug issues
- **Java Interop**: Fully usable from Java, not just Kotlin

---

This framework is built to test **what your APIs deliver**, not how they are built - enabling stable, expressive test suites that support continuous refactoring and iteration.

## How?

Kotlin
```kotlin
val cannon = Cannon(
    baseUrl = "http://localhost:8080",
    configuration = listOf(
        BasicAuth("user", "pass")
    )
)

val potato = Potato(
    method = HttpMethod.POST,
    path = "/test",
    body = TextBody("{ \"message\": \"hi\" }"),
    configuration = listOf(
        ContentHeader(ContentType.JSON),
        ResultVerification { result ->
            assertEquals(200, result.statusCode)
            assertEquals("Hello", result.responseBody)
        }
    )
)


cannon.fire(potato)
```
Java

```java
Cannon cannon = new Cannon(
        "http://localhost:8080",
        List.of(
                new BasicAuth("user", "pass")
        )
);

Potato potato = new Potato(
        HttpMethod.POST,
        "/test",
        new TextBody("{ \"message\": \"hi\" }"),
        List.of(
                new ContentHeader(ContentType.JSON),
                new ResultVerification(result -> {
                    assertEquals(200, result.getStatusCode());
                    assertEquals("Hello", result.getResponseBodyAsString());
                })
        )
);

cannon.fire(potato);
```