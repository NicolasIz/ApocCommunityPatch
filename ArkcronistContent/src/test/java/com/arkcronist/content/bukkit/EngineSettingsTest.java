package com.arkcronist.content.bukkit;

import com.arkcronist.content.core.upload.UploadSettings;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** config.yml's hosting settings: the built-in server, http.external-url and upload. */
class EngineSettingsTest {

    private final List<String> warnings = new ArrayList<>();
    private final Logger logger = Logger.getAnonymousLogger();

    {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
    }

    @Test
    void anEmptyConfigServesFromTheBuiltInServer() throws Exception {
        EngineSettings settings = read("");
        assertEquals(EngineSettings.Hosting.BUILTIN, settings.hosting());
        assertNull(settings.upload());
        assertEquals("", settings.http().externalUrl());
        // The public address is looked up at start; the warning waits for that to fail.
        assertTrue(settings.http().detectAddress());
        assertFalse(settings.http().addressConfigured());
        assertEquals("127.0.0.1", settings.http().publicAddress(), "until the lookup answers");
        assertTrue(warnings.stream().noneMatch(w -> w.contains("http.public-address is not set")));

        warnings.clear();
        read("http:\n  detect-public-address: false\n");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("http.public-address is not set")));
    }

    @Test
    void fallbackPortsAndTheSanityCheckAreRead() throws Exception {
        EngineSettings defaults = read("");
        assertEquals(List.of(), defaults.http().fallbackPorts(), "none: the three after http.port");
        assertEquals(com.arkcronist.content.core.net.PublicAddress.SERVICES, defaults.http().addressServices());
        assertEquals(EngineSettings.Sanity.DEFAULT, defaults.sanity());

        EngineSettings set = read("""
                http:
                  port: 8163
                  public-address: "play.example.net"
                  fallback-ports: [8200, "8201", 8163, 70000, nope]
                  address-services: ["https://ip.example.org", "not a link"]
                sanity:
                  enabled: false
                  interval-seconds: 2
                  max-millis-per-tick: 50
                """);
        assertEquals(List.of(8200, 8201), set.http().fallbackPorts(), "its own port, and what is not a port, skipped");
        assertTrue(set.http().addressConfigured());
        assertEquals(List.of("https://ip.example.org"), set.http().addressServices());
        assertEquals(new EngineSettings.Sanity(false, 10, 20), set.sanity(), "kept within bounds");
        assertEquals(3, warnings.stream().filter(w -> w.startsWith("http.fallback-ports")).count());
    }

    @Test
    void numbersAndLoginsHaveTheirDefaults() throws Exception {
        EngineSettings settings = read("");
        assertTrue(settings.pack().modelData().enabled());
        assertEquals(10000, settings.pack().modelData().first());
        assertTrue(settings.delivery().afterLogin());

        EngineSettings changed = read("""
                pack:
                  custom-model-data:
                    enabled: false
                    first: 500
                delivery:
                  after-login: false
                """);
        assertFalse(changed.pack().modelData().enabled());
        assertEquals(500, changed.pack().modelData().first());
        assertFalse(changed.delivery().afterLogin());
    }

    @Test
    void gunsLiquidsAndHudsHaveTheirDefaultsAndLimits() throws Exception {
        EngineSettings settings = read("");
        assertEquals(1, settings.guns().threads());
        assertEquals(64, settings.liquids().changesPerTick());
        assertEquals(10, settings.liquids().contactTicks());
        assertEquals(1, settings.liquids().chunkReach());
        assertEquals(20, settings.huds().actionBarTicks());
        assertEquals(30, settings.huds().saveSeconds());
        assertTrue(settings.pack().negativeSpaces());

        EngineSettings changed = read("""
                pack:
                  negative-spaces: false
                guns:
                  threads: 99
                liquids:
                  changes-per-tick: 0
                  chunk-reach: 3
                huds:
                  action-bar-ticks: 1
                """);
        assertEquals(8, changed.guns().threads(), "capped");
        assertEquals(1, changed.liquids().changesPerTick(), "at least one");
        assertEquals(3, changed.liquids().chunkReach());
        assertEquals(2, changed.huds().actionBarTicks());
        assertFalse(changed.pack().negativeSpaces());
    }

    @Test
    void anExternalLinkReplacesTheBuiltInServer() throws Exception {
        EngineSettings settings = read("""
                http:
                  external-url: "https://cdn.example.com/packs/arkcronist.zip?v={sha1}"
                """);
        assertEquals(EngineSettings.Hosting.EXTERNAL, settings.hosting());
        assertEquals("https://cdn.example.com/packs/arkcronist.zip?v=abc", settings.http().externalUrl("abc"));
        assertFalse(warnings.stream().anyMatch(w -> w.contains("public-address")),
                "the built-in server's address does not matter then");
    }

    @Test
    void anExternalLinkThatIsNotHttpIsIgnored() throws Exception {
        EngineSettings settings = read("""
                http:
                  external-url: "ftp://files.example.com/pack.zip"
                """);
        assertEquals(EngineSettings.Hosting.BUILTIN, settings.hosting());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("http.external-url")));
    }

    @Test
    void theUploadSectionIsReadWhole() throws Exception {
        EngineSettings settings = read("""
                http:
                  external-url: "https://ignored.example.com/pack.zip"
                upload:
                  enabled: true
                  url: "https://storage.example.com/api/upload"
                  method: put
                  file-field: "attachment"
                  file-name: "server.zip"
                  fields:
                    folder: "minecraft"
                    expires: 30
                  headers:
                    Authorization: "Bearer secret"
                  response:
                    url-path: "files[0].url"
                  download-url: "https://cdn.example.com/{value}"
                  timeout-seconds: 9999
                  retries: 4
                  verify: false
                """);

        assertEquals(EngineSettings.Hosting.UPLOAD, settings.hosting(), "an upload wins over a fixed link");
        UploadSettings upload = settings.upload();
        assertNotNull(upload);
        assertEquals("https://storage.example.com/api/upload", upload.endpoint().toString());
        assertEquals("PUT", upload.method());
        assertEquals("attachment", upload.fileField());
        assertEquals("server.zip", upload.fileName());
        assertEquals(Map.of("folder", "minecraft", "expires", "30"), upload.fields());
        assertEquals(Map.of("Authorization", "Bearer secret"), upload.headers());
        assertEquals("files[0].url", upload.urlPath());
        assertNull(upload.urlPattern());
        assertEquals("https://cdn.example.com/{value}", upload.downloadUrl());
        assertEquals(Duration.ofSeconds(600), upload.timeout(), "clamped");
        assertEquals(4, upload.retries());
        assertFalse(upload.verify());
        assertFalse(warnings.stream().anyMatch(w -> w.contains("public-address")));
    }

    @Test
    void theUploadDefaults() throws Exception {
        UploadSettings upload = read("""
                upload:
                  enabled: true
                  url: "http://127.0.0.1:8899/upload"
                """).upload();
        assertNotNull(upload);
        assertEquals("POST", upload.method());
        assertEquals("file", upload.fileField());
        assertEquals("resource_pack.zip", upload.fileName());
        assertNull(upload.urlPath(), "no path: the whole answer is the link");
        assertEquals("{value}", upload.downloadUrl());
        assertEquals(Duration.ofSeconds(60), upload.timeout());
        assertEquals(2, upload.retries());
        assertTrue(upload.verify());
    }

    @Test
    void anUploadThatCannotWorkIsTurnedOffWithAWarning() throws Exception {
        assertNull(read("upload: {enabled: true, url: \"not a url\"}").upload());
        assertNull(read("upload: {enabled: true, url: \"https://x.example/up\", response: {url-pattern: \"(unclosed\"}}").upload());
        assertNull(read("upload: {enabled: false, url: \"https://x.example/up\"}").upload());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("upload.url")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("url-pattern")));
    }

    @Test
    void aMethodOtherThanPostOrPutFallsBackToPost() throws Exception {
        assertEquals("POST", read("upload: {enabled: true, url: \"https://x.example/up\", method: DELETE}").upload().method());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("upload.method")));
    }

    private EngineSettings read(String yaml) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return EngineSettings.read(config, "", logger);
    }
}
