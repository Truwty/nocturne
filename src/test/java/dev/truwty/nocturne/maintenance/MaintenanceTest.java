package dev.truwty.nocturne.maintenance;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.truwty.nocturne.performance.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaintenanceTest {
    @TempDir Path directory;

    private String release(String version, boolean pre, boolean marker) {
        var item = new JsonObject();
        item.addProperty("tag_name", version);
        item.addProperty("draft", false);
        item.addProperty("prerelease", pre);
        item.addProperty("html_url", "https://evil.example/track");
        var assets = new JsonArray();
        for (String name :
                List.of(
                        "nocturne-" + version.substring(1) + ".jar",
                        marker ? "nocturne-mc26.1.2.json" : "nocturne-mc26.2.json")) {
            var a = new JsonObject();
            a.addProperty("name", name);
            assets.add(a);
        }
        item.add("assets", assets);
        return item.toString();
    }

    @Test
    void diagnosticsAllowOnlyKnownLoaderNames() {
        for (String loader : List.of("fabric", "forge", "neoforge")) {
            var report = JsonParser.parseString(DiagnosticsReport.encode(Map.of("loader", loader))).getAsJsonObject();
            assertEquals(loader, report.get("loader").getAsString());
        }
        var rejected = JsonParser.parseString(DiagnosticsReport.encode(Map.of("loader", "private-player-name"))).getAsJsonObject();
        assertFalse(rejected.has("loader"));
    }

    @Test
    void updateArtifactsMustMatchTheRunningLoader() {
        var item = JsonParser.parseString(release("v1.0.0", false, true)).getAsJsonObject();
        var assets = item.getAsJsonArray("assets");
        assets.get(0).getAsJsonObject().addProperty("name", "nocturne-1.0.0-neoforge-mc1.21.11.jar");
        assets.get(1).getAsJsonObject().addProperty("name", "nocturne-neoforge-mc1.21.11.json");
        String catalog = "[" + item + "]";
        assertTrue(ReleaseCatalog.newest(catalog, "0.14.0", true, "1.21.11", "neoforge").isPresent());
        assertTrue(ReleaseCatalog.newest(catalog, "0.14.0", true, "1.21.11", "fabric").isEmpty());
        assertTrue(ReleaseCatalog.newest(catalog, "0.14.0", true, "1.21.11", "forge").isEmpty());
        assets.get(1).getAsJsonObject().addProperty("name", "nocturne-mc1.21.11.json");
        assertTrue(ReleaseCatalog.newest("[" + item + "]", "0.14.0", true, "1.21.11", "neoforge").isEmpty());
    }

    @Test
    void stableOutranksPrerelease() {
        assertTrue(ReleaseVersion.parse("1.0.0").compareTo(ReleaseVersion.parse("1.0.0-rc.9")) > 0);
    }

    @Test
    void numericPrereleaseOrdering() {
        assertTrue(
                ReleaseVersion.parse("v0.13.0-beta.10")
                                .compareTo(ReleaseVersion.parse("0.13.0-beta.2"))
                        > 0);
    }

    @Test
    void equalVersionsCompareEqual() {
        assertEquals(
                0,
                ReleaseVersion.parse("v0.13.0-beta.1")
                        .compareTo(ReleaseVersion.parse("0.13.0-beta.1")));
    }

    @Test
    void invalidVersionsAreRejected() {
        for (String value :
                List.of("../bad", "1.2", "01.2.3", "1.2.3-01", "1.2.3/evil", "9999999.0.0"))
            assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse(value));
    }

    @Test
    void previewRequiresOptIn() {
        assertTrue(
                ReleaseCatalog.newest(
                                "[" + release("v0.14.0-beta.1", true, true) + "]",
                                "0.13.0-beta.1",
                                false)
                        .isEmpty());
    }

    @Test
    void previewCanBeEnabled() {
        assertEquals(
                "v0.14.0-beta.1",
                ReleaseCatalog.newest(
                                "[" + release("v0.14.0-beta.1", true, true) + "]",
                                "0.13.0-beta.1",
                                true)
                        .orElseThrow()
                        .tag());
    }

    @Test
    void olderAndSameVersionsDoNotNotify() {
        assertTrue(
                ReleaseCatalog.newest(
                                "[" + release("v0.13.0-beta.1", true, true) + "]",
                                "0.13.0-beta.1",
                                true)
                        .isEmpty());
    }

    @Test
    void incompatibleMinecraftIsIgnored() {
        assertTrue(
                ReleaseCatalog.newest(
                                "[" + release("v1.0.0", false, false) + "]", "0.13.0-beta.1", true)
            .isEmpty());
  }

  @Test
  void targetSpecificJarAndMarkerAreRequiredForModernPorts() {
    var item = JsonParser.parseString(release("v1.0.0", false, false)).getAsJsonObject();
    item.getAsJsonArray("assets")
        .get(0)
        .getAsJsonObject()
        .addProperty("name", "nocturne-1.0.0-mc26.2.jar");
    String catalog = "[" + item + "]";
    assertTrue(ReleaseCatalog.newest(catalog, "0.13.0", true, "26.2").isPresent());
    assertTrue(ReleaseCatalog.newest(catalog, "0.13.0", true, "26.3").isEmpty());
    item.getAsJsonArray("assets")
        .get(1)
        .getAsJsonObject()
        .addProperty("name", "nocturne-mc26.3.json");
    assertTrue(ReleaseCatalog.newest("[" + item + "]", "0.13.0", true, "26.3")
                        .isEmpty());
    }

    @Test
    void newestIsSelectedRegardlessOfCatalogOrder() {
        String json =
                "["
                        + release("v0.14.0", false, true)
                        + ","
                        + release("v1.0.0", false, true)
                        + ","
                        + release("v0.15.0", false, true)
                        + "]";
        assertEquals(
                "v1.0.0", ReleaseCatalog.newest(json, "0.13.0-beta.1", true).orElseThrow().tag());
    }

    @Test
    void malformedEntryDoesNotHideValidRelease() {
        assertTrue(
                ReleaseCatalog.newest(
                                "[{}," + release("v1.0.0", false, true) + "]",
                                "0.13.0-beta.1",
                                true)
                        .isPresent());
    }

    @Test
    void draftAndMissingJarAreIgnored() {
        var item = JsonParser.parseString(release("v1.0.0", false, true)).getAsJsonObject();
        item.addProperty("draft", true);
        assertTrue(ReleaseCatalog.newest("[" + item + "]", "0.1.0", true).isEmpty());
        item.addProperty("draft", false);
        item.getAsJsonArray("assets").remove(0);
        assertTrue(ReleaseCatalog.newest("[" + item + "]", "0.1.0", true).isEmpty());
    }

    @Test
    void changelogCannotBeRedirectedByApiPayload() {
        var found =
                ReleaseCatalog.newest("[" + release("v1.0.0", false, true) + "]", "0.1.0", true)
                        .orElseThrow();
        assertEquals(
                "https://github.com/Truwty/nocturne/releases/tag/v1.0.0",
                found.changelog().toString());
    }

    @Test
    void oversizedCatalogIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReleaseCatalog.newest(" ".repeat(262145), "0.1.0", true));
    }

    @Test
    void diagnosticAllowlistDropsPrivateFields() {
        var facts = new HashMap<String, Object>();
        facts.put("song", "PRIVATE_SONG");
        facts.put("token", "PRIVATE_TOKEN");
        facts.put("server", "PRIVATE_SERVER");
        facts.put("path", "C:/Users/private");
        facts.put("enabled", true);
        facts.put("os", "Windows");
        String json = DiagnosticsReport.encode(facts);
        assertFalse(json.contains("PRIVATE"));
        assertFalse(json.contains("C:/"));
        assertTrue(json.contains("Windows"));
        assertTrue(json.contains("enabled"));
    }

    @Test
    void diagnosticValuesMustHaveApprovedTypes() {
        String json =
                DiagnosticsReport.encode(
                        Map.of(
                                "enabled",
                                "TOKEN_SECRET",
                                "os",
                                "EMAIL_SECRET",
                                "recovery",
                                "PATH_SECRET",
                                "particles",
                                Double.NaN,
                                "frameCpuMs",
                                Double.POSITIVE_INFINITY,
                                "modVersion",
                                "PRIVATE_SECRET"));
        assertFalse(json.contains("SECRET"));
        assertFalse(json.contains("NaN"));
        assertFalse(json.contains("Infinity"));
    }

    @Test
    void diagnosticFileMatchesFilteredPreview() throws Exception {
        var facts = Map.<String, Object>of("enabled", true, "song", "SECRET");
        Path saved = DiagnosticsReport.write(directory, facts);
        assertEquals(DiagnosticsReport.encode(facts), Files.readString(saved));
        assertTrue(saved.startsWith(directory));
        assertFalse(Files.readString(saved).contains("SECRET"));
    }

    private JsonObject budget() {
        return JsonParser.parseString(
                        "{\"cpuP95Ms\":50,\"minText\":1,\"text\":128,\"vertices\":512,\"particles\":0,\"glow\":0,\"cloud\":0,\"trails\":0}")
                .getAsJsonObject();
    }

    private RegressionBudget probe(double cpu, int text, int particles) {
        var p = new RegressionBudget();
        for (int i = 1; i <= 40; i++)
            p.add(new RenderPerformance.Snapshot(cpu, text, 0, 0, 0, particles, 0, 0, 0, 1, 16, i));
        return p;
    }

    @Test
    void fixedWorkloadProducesPercentiles() {
        var report = probe(.2, 10, 0).report("steady-v1", budget());
        assertEquals(40, report.get("samples").getAsInt());
        assertEquals(.2, report.get("cpuP95Ms").getAsDouble());
    }

    @Test
    void geometryRegressionFails() {
        assertThrows(
                IllegalStateException.class, () -> probe(.2, 129, 0).report("steady-v1", budget()));
    }

    @Test
    void particlesCannotExceedBudget() {
        assertThrows(
                IllegalStateException.class, () -> probe(.2, 10, 1).report("steady-v1", budget()));
    }

    @Test
    void CpuRegressionFails() {
        assertThrows(
                IllegalStateException.class, () -> probe(51, 10, 0).report("steady-v1", budget()));
    }

    @Test
    void missingLyricsCannotPassPerformanceGate() {
        assertThrows(
                IllegalStateException.class, () -> probe(.1, 0, 0).report("steady-v1", budget()));
    }

    @Test
    void invalidMetricsAndInsufficientSamplesFail() {
        assertThrows(
                IllegalStateException.class,
                () -> probe(Double.NaN, 10, 0).report("steady-v1", budget()));
        assertThrows(
                IllegalStateException.class,
                () -> new RegressionBudget().report("steady-v1", budget()));
    }
}
