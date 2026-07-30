package com.dbtraining.reconx.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ============================================================================
 * TICKET-ADV061 — Structured logging with MDC
 *
 * WHAT:    Structural tests over logback-spring.xml plus a rendering check that
 *          the dev pattern actually emits the MDC correlationId.
 * HOW:     The XML is parsed straight off the classpath and asserted with XPath
 *          rather than booting Spring — logback-spring.xml is read by the
 *          logging subsystem before the context exists, so a @SpringBootTest
 *          would prove nothing about profile selection anyway.
 * WHY:     Acceptance criteria name the exact elements (two springProfile
 *          blocks, MDC pattern tokens, LogstashEncoder with includeMdc and a
 *          service custom field), and a curl with foo-123 must show up in the
 *          dev log line — the PatternLayout render is that assertion offline.
 * ============================================================================
 */
class LogbackMdcConfigTest {

    private static final String CONFIG_RESOURCE = "/logback-spring.xml";

    private static Document config;
    private static XPath xpath;

    @BeforeAll
    static void parseConfig() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try (InputStream in = LogbackMdcConfigTest.class.getResourceAsStream(CONFIG_RESOURCE)) {
            assertThat(in).as("logback-spring.xml must exist on the classpath").isNotNull();
            config = factory.newDocumentBuilder().parse(new InputSource(in));
        }
        xpath = XPathFactory.newInstance().newXPath();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static String text(String expression) throws Exception {
        return ((String) xpath.evaluate(expression, config, XPathConstants.STRING)).trim();
    }

    private static Node node(String expression) throws Exception {
        return (Node) xpath.evaluate(expression, config, XPathConstants.NODE);
    }

    @Test
    @DisplayName("declares exactly two springProfile blocks: dev and uat,prod")
    void declaresDevAndUatProdProfiles() throws Exception {
        NodeList profiles = (NodeList) xpath.evaluate(
                "/configuration/springProfile", config, XPathConstants.NODESET);

        assertThat(profiles.getLength()).isEqualTo(2);
        assertThat(node("/configuration/springProfile[@name='dev']")).isNotNull();
        assertThat(node("/configuration/springProfile[@name='uat,prod']")).isNotNull();
    }

    @Test
    @DisplayName("dev profile uses a plain pattern carrying both MDC tokens")
    void devProfileUsesPlainPatternWithMdcTokens() throws Exception {
        String pattern = text("/configuration/springProfile[@name='dev']"
                + "/appender[@name='STDOUT']/encoder/pattern");

        assertThat(pattern)
                .contains("%X{correlationId:-}")
                .contains("%X{tradeRef:-}")
                .contains("%msg");
        assertThat(node("/configuration/springProfile[@name='dev']"
                + "/appender[@name='STDOUT']/encoder/@class"))
                .as("dev encoder must stay a plain pattern encoder")
                .isNull();
    }

    @Test
    @DisplayName("uat,prod profile uses LogstashEncoder with MDC and a service field")
    void uatProdProfileUsesLogstashEncoder() throws Exception {
        String encoderPath = "/configuration/springProfile[@name='uat,prod']"
                + "/appender[@name='STDOUT']/encoder";

        assertThat(text(encoderPath + "/@class"))
                .isEqualTo("net.logstash.logback.encoder.LogstashEncoder");
        assertThat(text(encoderPath + "/includeMdc")).isEqualTo("true");
        assertThat(text(encoderPath + "/customFields")).contains("\"service\"");
    }

    @Test
    @DisplayName("both profiles log to a console appender named STDOUT wired to the root logger")
    void bothProfilesWireConsoleAppenderToRoot() throws Exception {
        assertThat(text("/configuration/springProfile[@name='dev']/appender[@name='STDOUT']/@class"))
                .isEqualTo("ch.qos.logback.core.ConsoleAppender");
        assertThat(text("/configuration/springProfile[@name='uat,prod']/appender[@name='STDOUT']/@class"))
                .isEqualTo("ch.qos.logback.core.ConsoleAppender");
        assertThat(text("/configuration/root/@level")).isEqualTo("INFO");
        assertThat(text("/configuration/root/appender-ref/@ref")).isEqualTo("STDOUT");
    }

    @Test
    @DisplayName("dev pattern renders a supplied correlation id into the log line")
    void devPatternRendersCorrelationId() throws Exception {
        String pattern = text("/configuration/springProfile[@name='dev']"
                + "/appender[@name='STDOUT']/encoder/pattern");

        LoggerContext context = new LoggerContext();
        context.start();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.start();

        MDC.put("correlationId", "foo-123");
        MDC.put("tradeRef", "TRD-0001");
        LoggingEvent event = new LoggingEvent(
                LogbackMdcConfigTest.class.getName(),
                context.getLogger(LogbackMdcConfigTest.class),
                Level.INFO,
                "listing trades",
                null,
                null);
        event.setMDCPropertyMap(MDC.getCopyOfContextMap());

        String rendered = layout.doLayout(event);

        assertThat(rendered).contains("foo-123").contains("TRD-0001").contains("listing trades");

        layout.stop();
        context.stop();
    }

    @Test
    @DisplayName("dev pattern renders an empty MDC slot without blowing up")
    void devPatternToleratesEmptyMdc() throws Exception {
        String pattern = text("/configuration/springProfile[@name='dev']"
                + "/appender[@name='STDOUT']/encoder/pattern");

        LoggerContext context = new LoggerContext();
        context.start();
        PatternLayout layout = new PatternLayout();
        layout.setContext(context);
        layout.setPattern(pattern);
        layout.start();

        LoggingEvent event = new LoggingEvent(
                LogbackMdcConfigTest.class.getName(),
                context.getLogger(LogbackMdcConfigTest.class),
                Level.INFO,
                "no correlation id",
                null,
                null);
        event.setMDCPropertyMap(Map.of());

        String rendered = layout.doLayout(event);

        assertThat(rendered).contains("no correlation id").doesNotContain("foo-123");

        layout.stop();
        context.stop();
    }
}
