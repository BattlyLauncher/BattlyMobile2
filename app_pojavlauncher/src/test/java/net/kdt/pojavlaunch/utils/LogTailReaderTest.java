package net.kdt.pojavlaunch.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class LogTailReaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsOnlyTheRequestedTail() throws Exception {
        File log = temporaryFolder.newFile("latestlog.txt");
        Files.write(log.toPath(), ("old-data-that-must-not-be-read\n"
                + "Local game hosted on port 25565\n").getBytes(StandardCharsets.UTF_8));

        String tail = LogTailReader.readUtf8Tail(log, 36);

        assertTrue(tail.contains("hosted on port 25565"));
        assertFalse(tail.contains("old-data"));
    }
}
