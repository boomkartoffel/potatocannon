import io.github.boomkartoffel.potatocannon.TestBackend;
import io.github.boomkartoffel.potatocannon.cannon.Cannon;
import io.github.boomkartoffel.potatocannon.potato.HttpMethod;
import io.github.boomkartoffel.potatocannon.potato.Potato;
import io.github.boomkartoffel.potatocannon.strategy.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static io.github.boomkartoffel.potatocannon.strategy.ContextKt.resolveFromContext;

public class PotatoCannonJavaTest {

    private static Integer port;

    @BeforeAll
    public static void setUp() {
        port = new Random().ints(30_000, 60_000).findFirst().orElse(8080);
        TestBackend.INSTANCE.start(port);
    }

    @AfterAll
    public static void tearDown() {
        TestBackend.INSTANCE.stop();
    }

    private final Check is200 = result -> Assertions.assertEquals(200, result.getStatusCode());

    @Test
    public void getRequest_returnsHello() {
        Potato potato = new Potato(
                HttpMethod.GET,
                "/test",
                new Expectation(result -> Assertions.assertEquals("Hello", result.responseText()))
        );

        Cannon cannon = new Cannon(
                "http://localhost:" + port,
                List.of(
                        FireMode.SEQUENTIAL,
                        new Expectation(is200)
                )
        );

        cannon.fire(potato);
    }


    @Test
    public void context_works_in_Java() {

        var capture = new CaptureToContext("key", result -> "Hello");
        var retrieve = resolveFromContext(ctx -> {
            var key = ctx.get("key", String.class);
            return new QueryParam("fromContext", key);
        });

        Potato potato = new Potato(
                HttpMethod.GET,
                "/test",
                new Expectation(result -> Assertions.assertEquals("Hello", result.responseText())),
                capture
        );

        var potato2 = potato
                .withSettings(retrieve);

        Cannon cannon = new Cannon(
                "http://localhost:" + port,
                List.of(
                        FireMode.SEQUENTIAL,
                        new Expectation(is200)
                )
        );

        cannon.fire(potato, potato2);
    }


}