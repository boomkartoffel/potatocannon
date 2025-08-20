import io.github.boomkartoffel.potatocannon.*;
import io.github.boomkartoffel.potatocannon.cannon.*;
import io.github.boomkartoffel.potatocannon.potato.*;
import io.github.boomkartoffel.potatocannon.strategy.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Random;

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
                new Expectation(result -> {
                    Assertions.assertEquals("Hello", result.getResponseBody());
                })
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
}