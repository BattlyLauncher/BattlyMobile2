package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class MobileGluesBenchmarkResultTest {

    @Test
    public void parsesAndRanksEveryMeasuredEntry() {
        String json = "{"
                + "\"entries\":{"
                + "\"glMultiDrawArrays\":{\"unroll\":30,\"multiarrays\":10},"
                + "\"glMultiDrawElements\":{\"unroll\":20,\"indirect\":12},"
                + "\"glMultiDrawElementsBaseVertex\":{\"compute\":8,\"basevertex\":16},"
                + "\"glMultiDrawArraysIndirect\":{\"indirect\":4},"
                + "\"glMultiDrawElementsIndirect\":{\"multiindirect\":3}"
                + "},"
                + "\"quality\":{\"glMultiDrawArrays\":{\"noise\":0.04,\"rounds\":9,\"attempts\":1,\"sections\":256,\"noisy\":false}},"
                + "\"elapsedMs\":8123,\"angleRequested\":false,\"angleInUse\":false,\"renderer\":\"Adreno\""
                + "}";

        MobileGluesBenchmarkResult result = MobileGluesBenchmarkResult.parse(json);

        assertTrue(result.isSuccessful());
        assertEquals(5, result.getRankings().size());
        assertEquals(Arrays.asList("multiarrays", "unroll", "multiindirect"),
                result.getRanking("glMultiDrawArrays"));
        assertEquals("multiarrays,unroll,multiindirect",
                result.getPreferenceValue("glMultiDrawArrays"));
        assertEquals(9, result.getQuality("glMultiDrawArrays").rounds);
        assertEquals(8123L, Math.round(result.getElapsedMs()));
    }

    @Test
    public void keepsUnmeasuredBackendsAfterMeasuredOnes() {
        MobileGluesBenchmarkResult result = MobileGluesBenchmarkResult.parse(
                "{\"entries\":{\"glMultiDrawElementsBaseVertex\":{\"unroll\":4,\"compute\":2}}}"
        );

        assertEquals(Arrays.asList(
                        "compute", "unroll", "multibasevertex", "multiindirect", "indirect", "basevertex"),
                result.getRanking("glMultiDrawElementsBaseVertex"));
    }

    @Test
    public void rejectsNativeErrorsAndUnparseableResults() {
        MobileGluesBenchmarkResult nativeError = MobileGluesBenchmarkResult.parse(
                "{\"error\":\"context-lost\",\"sections\":512}"
        );
        MobileGluesBenchmarkResult malformed = MobileGluesBenchmarkResult.parse("not-json");

        assertFalse(nativeError.isSuccessful());
        assertEquals("context-lost", nativeError.getError());
        assertEquals(512, nativeError.getSections());
        assertFalse(malformed.isSuccessful());
        assertEquals("unparseable result", malformed.getError());
    }

    @Test
    public void reportsDriverMismatchAndNoisyMeasurements() {
        MobileGluesBenchmarkResult result = MobileGluesBenchmarkResult.parse(
                "{\"entries\":{\"glMultiDrawArrays\":{\"unroll\":1}},"
                        + "\"angleRequested\":true,\"angleInUse\":false,"
                        + "\"quality\":{\"glMultiDrawArrays\":{\"noise\":0.3,\"rounds\":5,\"attempts\":4,\"sections\":1024,\"noisy\":true}}}"
        );

        assertTrue(result.hasDriverMismatch());
        assertTrue(result.hasNoisyEntries());
    }

    @Test
    public void decodesNativeAttemptAndProgress() {
        MobileGluesBenchmarkRunner.Progress progress =
                MobileGluesBenchmarkRunner.decodeProgress(2345);

        assertEquals(3, progress.attempt);
        assertEquals(0.345f, progress.fraction, 0.0001f);
        assertEquals(1f, MobileGluesBenchmarkRunner.decodeProgress(4000).fraction, 0f);
        assertEquals(null, MobileGluesBenchmarkRunner.decodeProgress(-1));
    }
}
