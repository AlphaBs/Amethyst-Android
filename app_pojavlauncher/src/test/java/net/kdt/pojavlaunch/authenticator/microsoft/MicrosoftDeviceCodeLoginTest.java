package net.kdt.pojavlaunch.authenticator.microsoft;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class MicrosoftDeviceCodeLoginTest {
    private MockWebServer server;
    private final AtomicLong time = new AtomicLong();
    private final List<Long> waits = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private MicrosoftDeviceCodeLogin client() {
        return new MicrosoftDeviceCodeLogin(
                server.url("/device").toString(),
                server.url("/token").toString(),
                time::get,
                milliseconds -> {
                    waits.add(milliseconds);
                    time.addAndGet(milliseconds * 1_000_000);
                }
        );
    }

    private void challenge(int expires) {
        server.enqueue(new MockResponse().setBody("{\"device_code\":\"private-device-value\","
                + "\"user_code\":\"TEST-CODE\",\"verification_uri\":\"https://www.microsoft.com/link\","
                + "\"expires_in\":" + expires + ",\"interval\":2}"));
    }

    private void error(String error) {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"" + error + "\",\"error_description\":\"private description\"}"));
    }

    @Test
    public void pendingAndSlowDownRespectServerIntervalAndUseDeviceGrant() throws Exception {
        challenge(900);
        error("authorization_pending");
        error("slow_down");
        server.enqueue(new MockResponse().setBody("{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\"}"));
        MicrosoftDeviceCodeLogin.Tokens tokens = client().authenticate(code -> {
            assertEquals("TEST-CODE", code.userCode);
            assertEquals("https://www.microsoft.com/link", code.verificationUri);
            assertEquals(900, code.expiresInSeconds);
        });
        assertEquals("test-access", tokens.accessToken);
        assertEquals("test-refresh", tokens.refreshToken);
        assertEquals(java.util.Arrays.asList(2000L, 2000L, 7000L), waits);
        RecordedRequest authorization = server.takeRequest();
        String body = authorization.getBody().readUtf8();
        assertEquals("/device", authorization.getPath());
        assertTrue(body.contains("response_type=device_code"));
        assertTrue(body.contains("client_id=" + MicrosoftDeviceCodeLogin.CLIENT_ID));
        assertTrue(body.contains("scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"));
        for (int poll = 0; poll < 3; poll++) {
            RecordedRequest request = server.takeRequest();
            assertEquals("/token", request.getPath());
            String form = request.getBody().readUtf8();
            assertTrue(form.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"));
            assertTrue(form.contains("device_code=private-device-value"));
            assertFalse(form.contains("TEST-CODE"));
        }
    }

    @Test
    public void declinedExpiredAndInvalidCodesStopPollingWithoutDisclosingResponse() throws Exception {
        for (String error : new String[]{"authorization_declined", "access_denied", "expired_token", "bad_verification_code"}) {
            challenge(900);
            error(error);
            MicrosoftDeviceCodeLogin.AuthenticationException failure = assertThrows(
                    MicrosoftDeviceCodeLogin.AuthenticationException.class,
                    () -> client().authenticate(code -> {})
            );
            assertEquals(error, failure.error);
            assertFalse(failure.getMessage().contains("private description"));
        }
        assertEquals(8, server.getRequestCount());
    }

    @Test
    public void localExpiryStopsBeforeAnOutOfDateTokenRequest() throws Exception {
        challenge(1);
        MicrosoftDeviceCodeLogin.AuthenticationException failure = assertThrows(
                MicrosoftDeviceCodeLogin.AuthenticationException.class,
                () -> client().authenticate(code -> {})
        );
        assertEquals("expired_token", failure.error);
        assertEquals(java.util.Collections.singletonList(1000L), waits);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void cancellationAfterDisplayingCodeDoesNotPoll() throws Exception {
        challenge(900);
        MicrosoftDeviceCodeLogin client = client();
        assertThrows(CancellationException.class, () -> client.authenticate(code -> client.cancel()));
        assertTrue(waits.isEmpty());
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void cancellationBeforeStartingMakesNoRequest() {
        MicrosoftDeviceCodeLogin client = client();
        client.cancel();
        assertThrows(CancellationException.class, () -> client.authenticate(code -> fail()));
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void rejectsNonMicrosoftVerificationAddressBeforeDisplayingCode() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"device_code\":\"test\",\"user_code\":\"test\","
                + "\"verification_uri\":\"https://microsoft.com.attacker.invalid/link\",\"expires_in\":900}"));
        MicrosoftDeviceCodeLogin.AuthenticationException failure = assertThrows(
                MicrosoftDeviceCodeLogin.AuthenticationException.class,
                () -> client().authenticate(code -> fail())
        );
        assertEquals("invalid_verification_uri", failure.error);
        assertEquals(1, server.getRequestCount());
    }
}
