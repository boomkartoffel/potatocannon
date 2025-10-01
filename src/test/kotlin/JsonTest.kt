package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.annotation.*
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.exception.JsonPathDecodingException
import io.github.boomkartoffel.potatocannon.exception.JsonPathTypeException
import io.github.boomkartoffel.potatocannon.potato.BodyFromObject
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextPotatoBody
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.properties.Delegates
import kotlin.random.Random


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonTest {

    var port by Delegates.notNull<Int>()

    var baseCannon by Delegates.notNull<Cannon>()

    @BeforeAll
    fun setUp() {
        port = Random.nextInt(30_000, 60_000)
        TestBackend.start(port)
        baseCannon = Cannon(
            baseUrl = "http://127.0.0.1:$port",
        )
    }

    @AfterAll
    fun tearDown() {
        TestBackend.stop()
    }


    private object JsonModels {
        data class Nested(
            val check: Int,
            val value: String
        )

        data class TestResponse(
            val x: String = "x",
            val num: Int = 1,
            val list: List<String> = listOf("a", "b", "c"),
            val nullValue: String? = null,
            val nested: List<Nested> = listOf(Nested(1, "a"), Nested(2, "b"), Nested(3, "c")),
            val nestedObject: Nested = Nested(4, "d"),
            val bool: Boolean = true
        )
    }


    @Test
    fun `JSON annotations are correctly used on serialization`() {
        // ── Models under test ───────────────────────────────────────────────────────
        @JsonRoot("TestRoot")
        data class Test1(val x: String = "1")

        // No JsonRoot → must serialize without wrapper
        data class Test2(val x: String = "2")

        // Rename a property for (de)serialization
        data class Renamed(
            @JsonName("userId") val id: Int = 7,
            val name: String = "Max"
        )

        data class OmitField(
            val x: String,
            val y: String,
            @JsonOmitNull val z: String? = null
        )

        class Ignore(
            val x: String = "1",
            @JsonIgnore
            val y: String = "2",
        )

        // class-level
        @JsonOmitNull
        data class OmitNullOnClass(
            val x: String? = null,
            val y: String? = null
        )

        // Control JSON property order
        @JsonPropertyOrder("b", "a")
        data class Ordered(val a: Int = 1, val b: Int = 2)

        @JsonOmitNull // omit nulls by default
        data class OmitNullClass2(
            val field1: String = "1",
            @JsonOmitNull(false) val field2: String? = null, // force include as null
            val field3: String? = null                            // still omitted
        )

        @JsonOmitNull // omit nulls by default
        data class WrapperOmitNull2(
            @JsonOmitNull(false) val omitNullClass2: OmitNullClass2? = null  // force include as null
        )


        // ── Expectations ────────────────────────────────────────────
        // Root wrapping appears only when @JsonRoot present
        val testRootProvided = Check {
            it.requestBody?.getContentAsString()!!.shouldContain("TestRoot")
        }.withDescription("TestRoot is provided in Serialization")

        val testRootMissing = Check {
            it.requestBody?.getContentAsString()!!.shouldNotContain("TestRoot")
        }.withDescription("TestRoot is NOT provided in Serialization")

        // JsonName renames "id" -> "userId"
        val renamedHasUserId = Check {
            val s = it.requestBody?.getContentAsString()!!
            s.shouldContain("userId")
            s.shouldNotContain("id")
        }.withDescription("@JsonName renames id -> userId")

        val omitNullTestObject = OmitField(
            x = "x",
            y = "y",
            z = null
        )

        val omitNullTest1 = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""x":"x"""")
            body.shouldContain(""""y":"y"""")
            body.shouldNotContain(""""z"""")
            body.shouldNotContain(":null")
        }.withDescription("@JsonOmitNull omits null field when put on property level")

        val omitInClassObject = OmitNullOnClass(
            x = "x"
        )

        val omitNullTest2 = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""x":"x"""")
            body.shouldNotContain(""""y"""")
            body.shouldNotContain(":null")
        }.withDescription("@JsonOmitNull omits null field when put on class level")


        // JsonPropertyOrder enforces "b" before "a"
        val orderedKeys = Check {
            val s = it.requestBody?.getContentAsString()!!
            val bIdx = s.indexOf("b")
            val aIdx = s.indexOf("a")
            require(bIdx >= 0 && aIdx >= 0) { "Missing keys in JSON: $s" }
            require(bIdx < aIdx) { "Expected 'b' before 'a' in: $s" }
        }.withDescription("@JsonPropertyOrder puts b before a")


        val jsonIgnore = Check {
            val s = it.requestBody?.getContentAsString()!!
            s.shouldContain(""""x"""")
            s.shouldNotContain(""""y"""")
        }.withDescription("@JsonIgnore omits the field from serialization")

        val includeNullOverrideOnProperty = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""field1":"1"""")
            body.shouldContain(""""field2":null""")
            body.shouldNotContain(""""field3"""")
        }.withDescription("@JsonOmitNull(false) on property overrides class-level omission and includes null")

        val includeNullOverrideOnContainer = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""omitNullClass2":null""")
        }.withDescription("@JsonOmitNull(false) on containing property includes null even when class omits by default")


        // ── Potatoes ───────────────────────────────────────────────────────────────
        val basePotato = Potato(
            method = HttpMethod.POST,
            path = "/test"
        )

        val p1 = basePotato
            .withBody(BodyFromObject.json(Test1()))
            .addSettings(testRootProvided)

        val p2 = basePotato
            .withBody(BodyFromObject.json(Test2()))
            .addSettings(testRootMissing)

        val p3 = basePotato
            .withBody(BodyFromObject.json(Renamed()))
            .addSettings(renamedHasUserId)

        val p4 = basePotato
            .withBody(BodyFromObject.json(omitNullTestObject))
            .addSettings(omitNullTest1)

        val p5 = basePotato
            .withBody(BodyFromObject.json(omitInClassObject))
            .addSettings(omitNullTest2)

        val p6 = basePotato
            .withBody(BodyFromObject.json(Ordered()))
            .addSettings(orderedKeys)

        val p7 = basePotato
            .withBody(BodyFromObject.json(Ignore()))
            .addSettings(jsonIgnore)

        val p8 = basePotato
            .withBody(BodyFromObject.json(OmitNullClass2()))
            .addSettings(includeNullOverrideOnProperty)

        val p9 = basePotato
            .withBody(BodyFromObject.json(WrapperOmitNull2()))
            .addSettings(includeNullOverrideOnContainer)


        baseCannon
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(p1, p2, p3, p4, p5, p6, p7, p8, p9)
    }

    @Test
    fun `JSON annotations are correctly used on deserialization`() {

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/mirror"
        )

        data class Ignored(
            val x: String,
            @JsonIgnore
            val y: String?,
        )

        val bodySentIgnored = """
            {
                "x": "value1",
                "y": "value2"
            }
        """.trimIndent()

        val expectIgnoreWorks = Check { result: Result ->
            val obj = result.bodyAsObject(Ignored::class.java)
            obj.x shouldBe "value1"
            obj.y shouldBe null
        }.withDescription("@JsonIgnore works on deserialization")

        data class JsonNameTest(
            @JsonName("a")
            val x: Int
        )

        val bodySentJsonName = """{"a":7}"""

        val expectJsonName = Check { result: Result ->
            val obj = result.bodyAsObject(JsonNameTest::class.java)
            assertEquals(7, obj.x)
        }.withDescription("@JsonName maps external name")

        data class AliasesTest(
            @JsonAliases("uid", "xid")
            val id: Int
        )

        val bodiesAliases = listOf(
            """{"uid":1}""",
            """{"xid":1}""",
            """{"id":1}"""
        )

        val expectJsonAlias = Check { result: Result ->
            val obj = result.bodyAsObject(AliasesTest::class.java)
            obj.id shouldBe 1
        }.withDescription("@JsonAliases maps alternative names")

        @JsonRoot("TestRoot")
        data class RootTest(val x: String)

        val bodySentRoot = """{"TestRoot":{"x":"abc"}}"""

        val expectRootTest = Check { result: Result ->
            val obj = result.bodyAsObject(RootTest::class.java)
            obj.x shouldBe "abc"
        }.withDescription("@JsonRoot deserialization unwrap")

        val bodySentRootList = """
        [
          {"TestRoot":{"x":"a"}},
          {"TestRoot":{"x":"b"}},
          {"TestRoot":{"x":"c"}}
        ]
    """.trimIndent()

        val expectRootList = Check { result: Result ->
            val items = result.bodyAsList(RootTest::class.java)
            items.size shouldBe 3
            items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("@JsonRoot deserialization unwrap for list elements")

        baseCannon
            .fire(
                potato.withBody(TextPotatoBody(bodySentIgnored)).addSettings(expectIgnoreWorks),
                potato.withBody(TextPotatoBody(bodySentJsonName)).addSettings(expectJsonName),
                *bodiesAliases.map {
                    potato.withBody(TextPotatoBody(it)).addSettings(expectJsonAlias)
                }.toTypedArray(),
                potato.withBody(TextPotatoBody(bodySentRoot)).addSettings(expectRootTest),
                potato.withBody(TextPotatoBody(bodySentRootList)).addSettings(expectRootList)
            )

    }

    @Test
    fun `JsonPath can be used to access response`() {

        val expectJsonPathX = Check { result ->
            val xValue = result.jsonPath("$.x").asText()
            xValue shouldBe "x"
        }.withDescription("JsonPath can read the value of x")

        val expectJsonPathXDifferentStyle = Check { result ->
            result.jsonPath("$")["x"].asText() shouldBe "x"
        }.withDescription("JsonPath can read the value of x in a different style")

        val expectJsonPathListValue = Check { result ->
            val listValue = result.jsonPath("$.list[1]").asText()
            listValue shouldBe "b"
        }.withDescription("JsonPath can read the second value of the list")

        val expectJsonPathListValue2 = Check { result ->
            val listValues = result.jsonPath("$.list[0,2]").asArray()
            listValues.first().asText() shouldBe "a"
            listValues[1].asText() shouldBe "c"
        }.withDescription("JsonPath can read the second value of the list and extract it as a list")

        val expectJsonPathNestedFind = Check { result ->
            val nestedValue = result.jsonPath("$.nested[?(@.check==2)].value")
            nestedValue[0].asText() shouldBe "b"
        }.withDescription("JsonPath can find in nested lists")

        val expectJsonPathNestedFind2 = Check { result ->
            val nestedValues = result.jsonPath("$.nested[?(@.check>=2)].value").asArray()
            nestedValues.size() shouldBe 2
            nestedValues[0].asText() shouldBe "b"
            nestedValues[1].asText() shouldBe "c"
        }.withDescription("JsonPath can find in nested lists and extract as list")

        val checkNumber = Check { result ->
            val number = result.jsonPath("$.num").asInt()
            number shouldBe 1
        }.withDescription("JsonPath can read the value of num as an integer")

        val checkNested = Check { result ->
            val allNested = result.jsonPath("$..check").asArray()
            allNested.size() shouldBe 4
            allNested[0].asInt() shouldBe 1
            allNested[1].asInt() shouldBe 2
            allNested[2].asInt() shouldBe 3
            allNested[3].asInt() shouldBe 4
        }.withDescription("JsonPath can read all 'check' values with recursive descent")

        val checkNumber2 = Check { result ->
            val number = result.jsonPath("$")
            number["num"].asInt() shouldBe 1
        }.withDescription("JsonPath can read the value of num as an integer - 2")

        val checkNull = Check { result ->
            val nullValue = result.jsonPath("$.nullValue")
            nullValue.isMissing() shouldBe false
            nullValue.isNull() shouldBe true
        }.withDescription("JsonPath fails on null value")

        val checkNestedWithFieldGetter = Check { result ->
            val obj = result.jsonPath("$")
            obj["nestedObject"]["value"].asText() shouldBe "d"
        }.withDescription("PathMatch can be read with Field names")

        val potato = Potato.post(
            "/mirror",
            BodyFromObject.json(JsonModels.TestResponse()),
            expect200StatusCode,
            expectJsonPathX,
            expectJsonPathXDifferentStyle,
            expectJsonPathListValue,
            expectJsonPathListValue2,
            expectJsonPathNestedFind,
            expectJsonPathNestedFind2,
            checkNumber,
            checkNested,
            checkNumber2,
            checkNull,
            checkNestedWithFieldGetter
        )

        val json2 = """
            {
              "items": [
                { "id": 1, "name": "Banana" },
                { "id": 2, "name": "apple" },
                { "id": 3, "name": "blueberry" },
                { "id": 4, "name": "Cherry" },
                { "id": 5, "name": "beet" }
              ]
            }
        """.trimIndent()

        val expectRegexFind = Check {
            val matches = it
                .jsonPath("$.items[?(@.name =~ /(?i)^b.*$/)].name")
                .asArray()

            matches.size() shouldBe 3
            matches.first().asText() shouldBe "Banana"
            matches[1].asText() shouldBe "blueberry"
            matches[2].asText() shouldBe "beet"
        }.withDescription("Jayway Regex search works")

        val expectIdsFind = Check {
            val items = it.jsonPath("$.items")
            val ids = items["id"]

            ids.asArray().size() shouldBe 5
            ids[0].asInt() shouldBe 1
            ids[1].asInt() shouldBe 2
            ids[2].asInt() shouldBe 3

        }.withDescription("Array find by name works")

        val json3 = "{ \"numbers\": [1, 2, 3, 4.5] }"

        val expectedMaxSumFind = Check {
            it.jsonPath("$.numbers.max()").asDouble() shouldBe 4.5
            it.jsonPath("$.numbers.sum()").asDouble() shouldBe 10.5
        }.withDescription("Jayway Max and Sum works")

        val json4 = """
                {
                  "meta": { "maxPrice": 25.0 },
                  "items": [
                    { "id": 1, "name": "A", "price": 9.99 },
                    { "id": 2, "name": "B", "price": 25.0 },
                    { "id": 3, "name": "C", "price": 19.5 },
                    { "id": 4, "name": "D", "price": 30.0 }
                  ]
                }
            """.trimIndent()

        val expextFindWithComparison = Check {
            val matches = it.jsonPath("$.items[?(@.price < $.meta.maxPrice)]").asArray()
            matches.size() shouldBe 2
            matches.first()["name"].asText() shouldBe "A"
            matches.first()["price"].asDouble() shouldBe 9.99
            matches[1]["name"].asText() shouldBe "C"
            matches[1]["price"].asDouble() shouldBe 19.5
        }

        val otherPotato = Potato.post("/mirror", TextPotatoBody(json2), expectRegexFind, expectIdsFind)
        val maxSumPotato = Potato.post("/mirror", TextPotatoBody(json3), expectedMaxSumFind)
        val findComparisonPotato = Potato.post("/mirror", TextPotatoBody(json4), expextFindWithComparison)

        baseCannon
            .fire(potato, otherPotato, maxSumPotato, findComparisonPotato)

    }


    @Test
    fun `JsonPath full contract happy and error cases`() {
        val potato = Potato.post(
            "/mirror",
            BodyFromObject.json(JsonModels.TestResponse()),
            expect200StatusCode
        )

        // Missing path: ensurePresent() branch -> "Expected value ... but was missing"
        val expectMissingPathEnsurePresent = Check { result ->
            val ex = runCatching {
                result.jsonPath("$.nonExisting").asText()  // triggers ensurePresent()
            }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected value at $.nonExisting but was missing"
        }.withDescription("Missing path -> ensurePresent throws with 'but was missing'")


        val expectInvalidJsonPath = Check { result ->
            val ex = runCatching {
                result.jsonPath("$.[x[").asText()
            }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathDecodingException>()
            ex.message shouldBe "Failed to evaluate JSONPath '$.[x['"
        }.withDescription("Invalid JsonPath throws JsonPathDecodingException")

        val expectMissingVsNull = Check { result ->
            // missing
            val missing = result.jsonPath("$.nonExisting")
            missing.isMissing() shouldBe true
            missing.isPresent() shouldBe false
            missing.isNull() shouldBe false

            // present but null
            val nul = result.jsonPath("$.nullValue")
            nul.isMissing() shouldBe false
            nul.isNull() shouldBe true

            // present non-null
            val present = result.jsonPath("$.x")
            present.isPresent() shouldBe true
            present.isNull() shouldBe false
        }.withDescription("Distinguish missing vs JSON null vs present value")

        val expectNullTypeErrors = Check { result ->
            val ex = runCatching { result.jsonPath("$.nullValue").asText() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected text at $.nullValue but was null"
        }.withDescription("JSON null still trips type guards")

        // Type mismatch cases
        val expectTextToIntMismatch = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asInt() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected int at $.x but was text"
        }.withDescription("asInt on text throws type exception")

        val expectTextToLongMismatch = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asLong() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected long at $.x but was text"
        }.withDescription("asLong on text throws type exception")

        val expectTextToDoubleMismatch = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asDouble() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected double at $.x but was text"
        }.withDescription("asDouble on text throws type exception")

        val expectTextToBooleanMismatch = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asBoolean() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected boolean at $.x but was text"
        }.withDescription("asBoolean on text throws type exception")

        // Happy-path numeric reads
        val expectNumericReads = Check { result ->
            result.jsonPath("$.num").asInt() shouldBe 1
            result.jsonPath("$.num").asLong() shouldBe 1L
            result.jsonPath("$.num").asDouble() shouldBe 1.0
        }.withDescription("Numeric nodes can be read as int/long/double")

        // Happy-path bool reads
        val expectBoolReads = Check { result ->
            result.jsonPath("$.bool").asBoolean() shouldBe true
        }.withDescription("Boolean nodes can be read as boolean")

        // asArray() success and failure
        val expectArrayHappyPath = Check { result ->
            val arr = result.jsonPath("$.nested").asArray()
            arr.size() shouldBe 3

            val onlyChecks = result.jsonPath("$.nested")["check"].asArray()

            onlyChecks.size() shouldBe 3
            onlyChecks[0].asInt() shouldBe 1
            onlyChecks[1].asInt() shouldBe 2
            onlyChecks[2].asInt() shouldBe 3

            // PathMatchArray[index] -> PathMatch
            arr[1]["value"].asText() shouldBe "b"
        }.withDescription("asArray on array works; index + field works")

        val expectAsArrayOnTextFails = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asArray() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected array at $.x but was text"
        }.withDescription("asArray on non-array throws")

        // operator get(index) on PathMatch
        val expectIndexOnArrayOutOfBounds = Check { result ->
            val ex = runCatching { result.jsonPath("$.list")[5] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Index 5 out of bounds (size=3) at $.list"
        }.withDescription("Index OOB on array throws")

        val expectIndexOnNonArray = Check { result ->
            val ex = runCatching { result.jsonPath("$.x")[0] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected array at $.x but was text"
        }.withDescription("Indexing non-array throws")

        // operator get(field) on object: missing field
        val expectMissingFieldOnObject = Check { result ->
            val ex = runCatching { result.jsonPath("$.nestedObject")["missing"] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected field 'missing' at $.nestedObject but was missing"
        }.withDescription("Missing field on object throws")

        // operator get(field) when node is neither object nor array
        val expectFieldAccessOnNonObjectNorArray = Check { result ->
            val ex = runCatching { result.jsonPath("$.x")["anything"] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected object or array at $.x but was text"
        }.withDescription("Field access on non-object/array throws")

        // operator get(field) on array of objects: successful projection
        val expectArrayOfObjectsProjection = Check { result ->
            val projected = result.jsonPath("$.nested")["value"].asArray()
            projected.size() shouldBe 3
            projected.first().asText() shouldBe "a"
            projected[2].asText() shouldBe "c"
        }.withDescription("Projecting a field across an array of objects returns an array of values")

        // operator get(field) on array, but the array isn't objects
        val expectArrayOfObjectsProjectionWrongType = Check { result ->
            val ex = runCatching { result.jsonPath("$.list")["value"] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected array of objects at $.list but element #0 was text"
        }.withDescription("Projecting field on array of non-objects throws")

        // Sanity happy paths for text & object field
        val expectHappyPaths = Check { result ->
            result.jsonPath("$.x").asText() shouldBe "x"
            result.jsonPath("$.nestedObject")["value"].asText() shouldBe "d"
            result.jsonPath("$.nested")[0]["check"].asInt() shouldBe 1
        }.withDescription("Happy-path reads for text, field on object, and array element")

        val expectAsObjectHappyPath = Check { result ->
            val obj = result.jsonPath("$.nestedObject").asObject()
            obj.size() shouldBe 2
            obj.contains("check") shouldBe true
            obj.contains("missing") shouldBe false
            obj.keys().toSet() shouldBe setOf("check", "value")
            obj["value"].asText() shouldBe "d"
            obj["check"].asInt() shouldBe 4
        }.withDescription("asObject exposes size/contains/keys and field access on object")

        val expectAsObjectMissingField = Check { result ->
            val obj = result.jsonPath("$.nestedObject").asObject()
            val ex = runCatching { obj["missing"] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected field 'missing' at $.nestedObject but was missing"
        }.withDescription("Accessing missing field on asObject throws")

        val expectAsObjectOnNonObjectFails = Check { result ->
            val ex = runCatching { result.jsonPath("$.x").asObject() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected object at $.x but was text"
        }.withDescription("asObject on non-object throws")

        val expectAsObjectOnNullFails = Check { result ->
            val ex = runCatching { result.jsonPath("$.nullValue").asObject() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected object at $.nullValue but was null"
        }.withDescription("asObject on JSON null throws")

        val expectAsObjectOnArrayFails = Check { result ->
            val ex = runCatching { result.jsonPath("$.nested").asObject() }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected object at $.nested but was array"
        }.withDescription("asObject on array throws")

        baseCannon.fire(
            potato.addSettings(
                expectMissingPathEnsurePresent,
                expectInvalidJsonPath,
                expectMissingVsNull,
                expectNullTypeErrors,
                expectTextToIntMismatch,
                expectTextToLongMismatch,
                expectTextToDoubleMismatch,
                expectTextToBooleanMismatch,
                expectNumericReads,
                expectBoolReads,
                expectArrayHappyPath,
                expectAsArrayOnTextFails,
                expectIndexOnArrayOutOfBounds,
                expectIndexOnNonArray,
                expectMissingFieldOnObject,
                expectFieldAccessOnNonObjectNorArray,
                expectArrayOfObjectsProjection,
                expectArrayOfObjectsProjectionWrongType,
                expectHappyPaths,
                expectAsObjectHappyPath,
                expectAsObjectMissingField,
                expectAsObjectOnNonObjectFails,
                expectAsObjectOnNullFails,
                expectAsObjectOnArrayFails
            )
        )

        // ----- Payload with empty arrays to hit PathMatchArray.first() empty -----
        val potatoEmptyArrays = Potato.post(
            "/mirror",
            BodyFromObject.json(
                JsonModels.TestResponse(
                    list = emptyList(),
                    nested = emptyList(),
                    nestedObject = JsonModels.Nested(4, "d")
                )
            ),
            expect200StatusCode
        )

        val expectFirstOnEmptyArrayThrows = Check { result ->
            val ex1 = runCatching { result.jsonPath("$.list").asArray().first() }.exceptionOrNull()
            ex1.shouldBeTypeOf<JsonPathTypeException>()
            ex1.message shouldBe "Expected non-empty array at $.list but was empty"

            val ex2 = runCatching { result.jsonPath("$.nested").asArray().first() }.exceptionOrNull()
            ex2.shouldBeTypeOf<JsonPathTypeException>()
            ex2.message shouldBe "Expected non-empty array at $.nested but was empty"
        }.withDescription("first() on empty arrays throws with correct message")

        baseCannon.fire(
            potatoEmptyArrays.addSettings(
                expectFirstOnEmptyArrayThrows
            )
        )

        val potatoMissingFieldOnElement = Potato.post(
            "/mirror",
            BodyFromObject.json(
                mapOf(
                    "broken" to listOf(
                        mapOf("value" to "a"),
                        mapOf("check" to 2),   // missing "value"
                        mapOf("value" to "c")
                    )
                )
            ),
            expect200StatusCode
        )

        val expectProjectionMissingFieldOnElement = Check { result ->
            val ex = runCatching { result.jsonPath("$.broken")["value"] }.exceptionOrNull()
            ex.shouldBeTypeOf<JsonPathTypeException>()
            ex.message shouldBe "Expected field 'value' on element #1 at $.broken but was missing"
        }.withDescription("Projection fails when a field is missing on some array element")

        baseCannon.fire(
            potatoMissingFieldOnElement.addSettings(
                expectProjectionMissingFieldOnElement
            )
        )
    }

}
