# 🥔 Potato Cannon

The **Potato Cannon** is a lightweight, expressive HTTP testing library in Kotlin. It lets you model individual HTTP requests as **potatoes** and fire them from a configurable **cannon**, supporting sequential or parallel modes, rich configuration, and detailed logging.

---

## Features

- Fire HTTP requests declaratively (`Potato`)
- Batch requests with shared config (`Cannon`)
- Sequential or parallel execution of multiple potatoes (`FireMode`)
- Dynamic response verification via lambdas (`ResultVerification`)
- Supports both text and binary bodies
- Header and query param strategies
- Logging with configurable verbosity
- Java-friendly API

---

## Usage

Kotlin
```kotlin
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

val cannon = Cannon(
    baseUrl = "http://localhost:8080",
    configuration = listOf(
        BasicAuth("user", "pass")
    )
)

cannon.fire(potato)
```
Java

```java
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

Cannon cannon = new Cannon(
        "http://localhost:8080",
        List.of(
                new BasicAuth("user", "pass")
        )
);

cannon.fire(potato);
```