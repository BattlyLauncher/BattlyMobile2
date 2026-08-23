package net.kdt.pojavlaunch.battlysocial;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class BattlySocialApiTest {
    @Test
    public void battlyWorldsPresenceKeepsRoomTypeAndVersion() throws Exception {
        JSONObject payload = BattlySocialApi.buildPresencePayload(
                "playing", "1.21.8", null, true);
        JSONObject activity = payload.getJSONObject("activity");

        assertEquals("playing", payload.getString("state"));
        assertEquals("battlyworlds", activity.getString("type"));
        assertTrue(activity.getBoolean("battlyWorlds"));
        assertEquals("1.21.8", activity.getString("version"));
    }

    @Test
    public void multiplayerPresenceIncludesServerInActivityAndPayload() throws Exception {
        BattlySocialApi.Server server = new BattlySocialApi.Server(new JSONObject()
                .put("host", "play.example.net")
                .put("port", 25570)
                .put("name", "Example"));
        JSONObject payload = BattlySocialApi.buildPresencePayload(
                "playing", "1.20.1-forge", server, false);

        assertEquals("play.example.net", payload.getJSONObject("server").getString("host"));
        assertEquals(25570, payload.getJSONObject("activity")
                .getJSONObject("server").getInt("port"));
    }
}
