package com.poliku.polygoplus;

import com.google.gson.Gson;
import com.poliku.polygoplus.api.PolyGoApi;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression coverage for API contracts used by authentication and Campus Pulse.
 */
public class ExampleUnitTest {
    private final Gson gson = new Gson();

    @Test
    public void loginResponse_readsInheritedTokenOnce() {
        String json = "{\"success\":true,\"message\":\"OK\",\"token\":\"jwt-123\","
                + "\"user\":{\"id\":\"7\",\"name\":\"Maya Student\","
                + "\"is_verified\":true,\"verification_status\":\"approved\","
                + "\"is_banned\":false}}";

        PolyGoApi.LoginResponse response = gson.fromJson(json, PolyGoApi.LoginResponse.class);

        assertTrue(response.isSuccess());
        assertEquals("jwt-123", response.getToken());
        assertNotNull(response.user);
        assertEquals("7", response.user.id);
        assertEquals("Maya Student", response.user.name);
        assertTrue(response.user.verified);
        assertEquals("approved", response.user.verificationStatus);
        assertFalse(response.user.banned);
    }

    @Test
    public void pulseLikeRequest_matchesBackendContract() {
        PolyGoApi.PulseRequest request = PolyGoApi.PulseRequest.like("42", true);
        String json = gson.toJson(request);

        assertTrue(json.contains("\"action\":\"like\""));
        assertTrue(json.contains("\"pulse_id\":\"42\""));
        assertTrue(json.contains("\"liked\":true"));
    }

    @Test
    public void pulseCommentResponse_mapsCountsAndAuthorFields() {
        String json = "{\"success\":true,\"comment_count\":3,\"comment\":{"
                + "\"id\":\"9\",\"body\":\"Hello\",\"user_id\":\"7\","
                + "\"user_name\":\"Maya\",\"profile_pic_url\":\"https://example.test/a.jpg\","
                + "\"created_at\":123,\"is_mine\":true}}";

        PolyGoApi.PulseCommentResponse response = gson.fromJson(
                json, PolyGoApi.PulseCommentResponse.class);

        assertTrue(response.isSuccess());
        assertEquals(3, response.commentCount);
        assertEquals("9", response.comment.id);
        assertEquals("7", response.comment.userId);
        assertTrue(response.comment.mine);
    }
}
