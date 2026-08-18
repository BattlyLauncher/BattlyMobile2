package net.kdt.pojavlaunch.customcontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Test;

public class MinecraftServerSessionTrackerTest {
    @After
    public void resetTracker() {
        MinecraftServerSessionTracker.reset();
    }

    @Test
    public void tracksHostnameAndPortAcrossOutputChunks() {
        MinecraftServerSessionTracker.ingest("[Client thread/INFO]: Connecting to play.examp");
        MinecraftServerSessionTracker.ingest("le.net, 25570\n");

        MinecraftServerSessionTracker.Endpoint endpoint = MinecraftServerSessionTracker.getEndpoint();
        assertEquals("play.example.net", endpoint.host);
        assertEquals(25570, endpoint.port);
    }

    @Test
    public void tracksBracketedIpv6Endpoint() {
        MinecraftServerSessionTracker.ingest("Connecting to /[2001:db8::8]:25565\n");

        MinecraftServerSessionTracker.Endpoint endpoint = MinecraftServerSessionTracker.getEndpoint();
        assertEquals("2001:db8::8", endpoint.host);
        assertEquals(25565, endpoint.port);
    }

    @Test
    public void clearsRemoteEndpointForLocalWorldAndDisconnect() {
        MinecraftServerSessionTracker.inspectLine("Connecting to server.test:25565");
        MinecraftServerSessionTracker.inspectLine("Starting integrated minecraft server version 1.21");
        assertNull(MinecraftServerSessionTracker.getEndpoint());

        MinecraftServerSessionTracker.inspectLine("Connecting to server.test:25565");
        MinecraftServerSessionTracker.inspectLine("Connection lost: Disconnected");
        assertNull(MinecraftServerSessionTracker.getEndpoint());
    }
}
