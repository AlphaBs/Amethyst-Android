package net.kdt.pojavlaunch.authenticator.microsoft;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/** Microsoft의 기존 Xbox OAuth client에 대한 Device Code 인증. */
public final class MicrosoftDeviceCodeLogin {
    static final String CLIENT_ID = "00000000402b5328";
    static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private final String deviceEndpoint;
    private final String tokenEndpoint;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private volatile boolean cancelled;
    private volatile HttpURLConnection activeConnection;

    public interface CodeListener {
        void onCode(Authorization authorization);
    }

    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    public static final class Authorization {
        public final String userCode;
        public final String verificationUri;
        public final int expiresInSeconds;

        private Authorization(String userCode, String verificationUri, int expiresInSeconds) {
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresInSeconds = expiresInSeconds;
        }
    }

    public static final class Tokens {
        public final String accessToken;
        public final String refreshToken;

        private Tokens(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    public static final class AuthenticationException extends IOException {
        public final String error;

        private AuthenticationException(String error) {
            // 응답 본문에는 인증 정보가 포함될 수 있어 예외나 로그에 넣지 않는다.
            super("Microsoft 인증 오류: " + error);
            this.error = error;
        }
    }

    public MicrosoftDeviceCodeLogin() {
        this(
                "https://login.live.com/oauth20_connect.srf",
                "https://login.live.com/oauth20_token.srf",
                System::nanoTime,
                Thread::sleep
        );
    }

    MicrosoftDeviceCodeLogin(String deviceEndpoint, String tokenEndpoint,
                            LongSupplier nanoTime, Sleeper sleeper) {
        this.deviceEndpoint = deviceEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    /** 호출은 작업 스레드에서 수행하며 코드는 해당 스레드로 전달한다. */
    public Tokens authenticate(CodeListener listener) throws IOException, JSONException, InterruptedException {
        checkCancelled();
        long requestedAt = nanoTime.getAsLong();
        JSONObject challenge = post(deviceEndpoint, form(
                "client_id", CLIENT_ID,
                "scope", SCOPE,
                "response_type", "device_code"
        ));
        failIfError(challenge);
        String deviceCode = requiredString(challenge, "device_code");
        String userCode = requiredString(challenge, "user_code");
        String verificationUri = requiredString(challenge, "verification_uri");
        validateVerificationUri(verificationUri);
        int expires = challenge.getInt("expires_in");
        if (expires <= 0 || expires > 86400) {
            throw new AuthenticationException("invalid_response");
        }
        long deadline = requestedAt + expires * 1_000_000_000L;
        long intervalSeconds = Math.max(1, challenge.optInt("interval", 5));
        checkCancelled();
        listener.onCode(new Authorization(userCode, verificationUri, expires));

        while (true) {
            checkCancelled();
            long remainingNanos = deadline - nanoTime.getAsLong();
            if (remainingNanos <= 0) {
                throw new AuthenticationException("expired_token");
            }
            long waitMillis = Math.min(intervalSeconds * 1000, (remainingNanos + 999_999) / 1_000_000);
            sleeper.sleep(waitMillis);
            checkCancelled();
            if (nanoTime.getAsLong() >= deadline) {
                throw new AuthenticationException("expired_token");
            }
            JSONObject response;
            try {
                response = post(tokenEndpoint, form(
                        "client_id", CLIENT_ID,
                        "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                        "device_code", deviceCode
                ));
            } catch (SocketTimeoutException timeout) {
                // RFC 8628: 연결 시간 초과 시 다음 요청까지의 간격을 늘린다.
                intervalSeconds = Math.min(intervalSeconds * 2, 86400);
                continue;
            }
            checkCancelled();
            String error = response.optString("error", "");
            if (error.equals("authorization_pending")) {
                continue;
            }
            if (error.equals("slow_down")) {
                intervalSeconds = Math.min(intervalSeconds + 5, 86400);
                continue;
            }
            failIfError(response);
            return new Tokens(
                    requiredString(response, "access_token"),
                    requiredString(response, "refresh_token")
            );
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Microsoft 로그인이 취소됐습니다");
        }
    }

    private JSONObject post(String endpoint, String body) throws IOException, JSONException {
        checkCancelled();
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        activeConnection = connection;
        try {
            checkCancelled();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int status = connection.getResponseCode();
            InputStream stream;
            if (status >= 200 && status < 300) {
                stream = connection.getInputStream();
            } else {
                stream = connection.getErrorStream();
            }
            if (stream == null) {
                throw new AuthenticationException("http_" + status);
            }
            JSONObject response;
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    if (output.size() + count > 65536) {
                        throw new AuthenticationException("invalid_response");
                    }
                    output.write(buffer, 0, count);
                }
                response = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            }
            if ((status < 200 || status >= 300) && !response.has("error")) {
                throw new AuthenticationException("http_" + status);
            }
            return response;
        } finally {
            activeConnection = null;
            connection.disconnect();
        }
    }

    private static void failIfError(JSONObject response) throws AuthenticationException {
        String error = response.optString("error", "");
        if (!error.isEmpty()) {
            // 서버가 보낸 자유 형식 설명은 노출하지 않고 OAuth 오류 식별자만 사용한다.
            if (!error.matches("[a-zA-Z0-9_]{1,80}")) {
                error = "invalid_response";
            }
            throw new AuthenticationException(error);
        }
    }

    private static String requiredString(JSONObject response, String key) throws JSONException, AuthenticationException {
        String value = response.getString(key);
        if (value.isEmpty()) {
            throw new AuthenticationException("invalid_response");
        }
        return value;
    }

    private static void validateVerificationUri(String value) throws AuthenticationException {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || host == null || !(host.equals("microsoft.com")
                    || host.endsWith(".microsoft.com") || host.equals("login.live.com"))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException failure) {
            throw new AuthenticationException("invalid_verification_uri");
        }
    }

    private static String form(String... fields) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < fields.length; index += 2) {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(URLEncoder.encode(fields[index], "UTF-8"))
                    .append('=')
                    .append(URLEncoder.encode(fields[index + 1], "UTF-8"));
        }
        return result.toString();
    }
}
