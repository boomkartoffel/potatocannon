package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.annotation.*
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.marshalling.WireFormat
import io.github.boomkartoffel.potatocannon.potato.BodyFromObject
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextPotatoBody
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import kotlin.properties.Delegates
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class XmlTest {

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

    @Test
    fun `XML annotations are correctly used on deserialization`() {
        val potato = Potato(method = HttpMethod.POST, path = "/mirror")

        @XmlRoot("TestRoot")
        data class RootTest(
            val x: String
        )

        val xmlRoot = """
                <TestRoot>
                  <x>abc</x>
                </TestRoot>
            """.trimIndent()

        val expectRoot = Check { result: io.github.boomkartoffel.potatocannon.result.Result ->
            val obj = result.bodyAsObject(RootTest::class.java, WireFormat.XML)
            obj.x shouldBe "abc"
        }.withDescription("@XmlRoot + @XmlElement basic object")

        data class User(
            @XmlElement("userId") val id: Int,
            val name: String
        )

        val xmlUser = """
                <User>
                  <userId>7</userId>
                  <name>Max</name>
                </User>
            """.trimIndent()

        val expectUser = Check { result: io.github.boomkartoffel.potatocannon.result.Result ->
            val u = result.bodyAsObject(User::class.java, WireFormat.XML)
            u.id shouldBe 7
            u.name shouldBe "Max"
        }.withDescription("@XmlElement renames field on read")

        data class Node(
            @XmlAttribute("id") val id: String,
            val value: String
        )

        val xmlNode = """<Node id="N1"><value>foo</value></Node>""".trimIndent()

        val expectNode = Check { result: io.github.boomkartoffel.potatocannon.result.Result ->
            val n = result.bodyAsObject(Node::class.java, WireFormat.XML)
            n.id shouldBe "N1"
            n.value shouldBe "foo"
        }.withDescription("@XmlAttribute maps attribute on read")

        val xmlBagWrapped = """
                <Bag>
                  <bagItems>
                    <item><x>a</x></item>
                    <item><x>b</x></item>
                    <item><x>c</x></item>
                  </bagItems>
                </Bag>
            """.trimIndent()

        val expectBagWrapped = Check { result: io.github.boomkartoffel.potatocannon.result.Result ->
            val bag = result.bodyAsObject(XmlModels.BagWrapped::class.java, WireFormat.XML)
            bag.items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("@XmlElementWrapper(useWrapping=true) reads wrapped list")

        val xmlBagUnwrapped = """
                <Bag>
                  <item><x>a</x></item>
                  <item><x>b</x></item>
                  <item><x>c</x></item>
                </Bag>
            """.trimIndent()

        val xmlRootList = """
                <RootTests>
                  <TestRoot><x>a</x></TestRoot>
                  <TestRoot><x>b</x></TestRoot>
                  <TestRoot><x>c</x></TestRoot>
                </RootTests>
            """.trimIndent()

        val expectRootList = Check { result: Result ->
            val items = result.bodyAsList(RootTest::class.java, WireFormat.XML)
            items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("Top-level list of @XmlRoot elements")

        baseCannon
            .fire(
                potato.withBody(TextPotatoBody(xmlRoot)).addSettings(expectRoot),
                potato.withBody(TextPotatoBody(xmlUser)).addSettings(expectUser),
                potato.withBody(TextPotatoBody(xmlNode)).addSettings(expectNode),
                potato.withBody(TextPotatoBody(xmlBagWrapped)).addSettings(expectBagWrapped),
                potato.withBody(TextPotatoBody(xmlRootList)).addSettings(expectRootList)
            )
    }


    private object XmlModels {
        @XmlRoot("User")
        data class UserWithRoot(
            @XmlElement("UserId") val id: Int = 7,
            val name: String = "Max"
        )

        // No @XmlRoot -> should NOT be <User>
        data class UserNoRoot(val x: String = "1")

        // Attribute instead of element
        @XmlRoot("AttrUser")
        data class UserWithAttr(
            @XmlAttribute("id") val id: Int = 1,
            val name: String = "Max"
        )

        data class SimpleUser(val name: String)

        // Wrapper list
        @XmlRoot("Group")
        data class GroupWrapped(
            @XmlWrapperElement("Users")
            @XmlElement("User")
            val users: List<SimpleUser> = listOf(SimpleUser("A"), SimpleUser("B"))
        )

        // No wrapper list
        @XmlRoot("GroupFlat")
        data class GroupUnwrapped(
            @XmlUnwrap
            @XmlElement("User")
            val users: List<SimpleUser> = listOf(SimpleUser("A"), SimpleUser("B"))
        )


        // Text content (mixed attr + text)
        @XmlRoot("Note")
        data class Note(
            @XmlAttribute("lang") val lang: String = "en",
            @XmlText val text: String = "hello"
        )

        @XmlRoot("item")
        data class Item(@XmlElement("x") val x: String)

        @XmlRoot("Bag")
        data class BagWrapped(
            @XmlElement("bagItems")
            val items: List<Item>
        )

        @XmlRoot("Bag")
        data class BagUnwrapped(
            @XmlElementWrapper(useWrapping = false)
            val items: List<Item>
        )
    }

    @Test
    fun `XML annotations are correctly used`() {
        // ---------- Helpers ----------
        fun parseXml(s: String) = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(s)))

        val xp = XPathFactory.newInstance().newXPath()
        fun rootName(doc: Document) = xp.evaluate("local-name(/*)", doc)
        fun count(doc: Document, expr: String) =
            xp.evaluate("count($expr)", doc).toDouble().toInt()

        fun text(doc: Document, expr: String) =
            xp.evaluate(expr, doc)

        // ---------- Expectations ----------
        val expectRootProvided = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) == "User") { "root should be <User>, was <${rootName(d)}>" }
            require(count(d, "/User/UserId") == 1) { "expected <UserId> element" }
        }.withDescription("@XmlRoot + @XmlElement rename work")

        val expectRootNotProvided = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) != "User") { "should not be <User> when @XmlRoot missing" }
        }.withDescription("No @XmlRoot does not produce <User>")

        val expectAttribute = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) == "AttrUser")
            require(text(d, "/AttrUser/@id") == "1") { "id attribute missing" }
            require(count(d, "/AttrUser/id") == 0) { "id must be attribute, not element" }
        }.withDescription("@XmlAttribute produces attribute, not element")

        val expectWrappedList = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            count(d, "/Group/Users/User") shouldBe 2
        }.withDescription("@XmlElementWrapper(name=Users) wraps list")

        val expectUnwrappedList = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(count(d, "/GroupFlat/Users") == 0) { "should not have <Users> wrapper" }
            require(count(d, "/GroupFlat/User") == 2) { "expected 2 unwrapped <User> items" }
        }.withDescription("@XmlElementWrapper(useWrapping=false) unwraps list")

        val expectTextContent = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            val d = parseXml(body)
            require(text(d, "/Note/@lang") == "en")
            require(text(d, "normalize-space(/Note)") == "hello") { "text content missing" }
            body shouldNotContain "<text>"
            body shouldNotContain "</text>"
        }.withDescription("@XmlText puts value as element text")

        // ---------- Potatoes ----------
        val basePotato = Potato(method = HttpMethod.POST, path = "/test")

        val p1 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserWithRoot())).addSettings(expectRootProvided)
        val p2 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserNoRoot())).addSettings(expectRootNotProvided)
        val p3 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserWithAttr())).addSettings(expectAttribute)
        val p4 = basePotato.withBody(BodyFromObject.xml(XmlModels.GroupWrapped())).addSettings(expectWrappedList)
        val p5 = basePotato.withBody(BodyFromObject.xml(XmlModels.GroupUnwrapped())).addSettings(expectUnwrappedList)
        val p6 = basePotato.withBody(BodyFromObject.xml(XmlModels.Note())).addSettings(expectTextContent)

        // ---------- Fire ----------
        baseCannon.withFireMode(FireMode.SEQUENTIAL).fire(p1, p2, p3, p4, p5, p6)
//        baseCannon.withFireMode(FireMode.SEQUENTIAL).fire( p3)
    }

    @Test
    fun `XML with various attributes gets correctly printed to the log`() {
        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/xml-data-with-attributes"
        )

        baseCannon.fire(potatoSingle)

    }

}