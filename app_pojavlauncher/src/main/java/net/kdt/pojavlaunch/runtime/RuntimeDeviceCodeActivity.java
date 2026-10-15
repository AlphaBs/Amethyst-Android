package net.kdt.pojavlaunch.runtime;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftDeviceCodeLogin;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 인증 코드를 표시하고 다른 기기의 브라우저에서 Microsoft 로그인을 기다린다. */
public final class RuntimeDeviceCodeActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MicrosoftDeviceCodeLogin deviceLogin;
    private MicrosoftBackgroundLogin minecraftLogin;
    private Future<?> request;
    private int generation;
    private TextView status;
    private TextView code;
    private TextView address;
    private Button openBrowser;
    private Button copyCode;
    private Button retry;
    private MicrosoftDeviceCodeLogin.Authorization authorization;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        AmethystRuntime.initialize(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);

        TextView title = text(content, "Microsoft Device Code 로그인");
        title.setTextSize(24);
        text(content, "PC 또는 휴대폰에서 아래 Microsoft 주소를 열고 코드를 입력해 주세요.");
        status = text(content, "인증 코드를 요청하고 있습니다");
        code = text(content, "");
        code.setTextSize(32);
        code.setTextIsSelectable(true);
        address = text(content, "");
        address.setTextIsSelectable(true);
        openBrowser = button(content, "이 기기 브라우저에서 열기", view -> {
            if (authorization != null) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authorization.verificationUri)));
                } catch (android.content.ActivityNotFoundException unavailable) {
                    status.setText("다른 기기에서 표시된 Microsoft 주소를 열어 주세요");
                }
            }
        });
        copyCode = button(content, "코드 복사", view -> {
            if (authorization != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Microsoft 로그인 코드", authorization.userCode));
            }
        });
        retry = button(content, "새 코드로 다시 시도", view -> startLogin());
        button(content, "취소", view -> finish());
        startLogin();
    }

    private void startLogin() {
        cancelRequests();
        int attempt = generation;
        authorization = null;
        code.setText("");
        address.setText("");
        status.setText("인증 코드를 요청하고 있습니다");
        openBrowser.setEnabled(false);
        copyCode.setEnabled(false);
        retry.setEnabled(false);
        MicrosoftDeviceCodeLogin login = new MicrosoftDeviceCodeLogin();
        deviceLogin = login;
        request = executor.submit(() -> {
            try {
                MicrosoftDeviceCodeLogin.Tokens tokens = login.authenticate(value -> runOnUiThread(() -> {
                    if (!isCurrent(attempt)) {
                        return;
                    }
                    authorization = value;
                    code.setText(value.userCode);
                    address.setText(value.verificationUri);
                    status.setText("브라우저 로그인을 기다리고 있습니다 · 코드 유효 시간 "
                            + value.expiresInSeconds + "초");
                    openBrowser.setEnabled(true);
                    copyCode.setEnabled(true);
                }));
                runOnUiThread(() -> {
                    if (!isCurrent(attempt)) {
                        return;
                    }
                    status.setText("Xbox 및 Minecraft 계정을 확인하고 있습니다");
                    openBrowser.setEnabled(false);
                    copyCode.setEnabled(false);
                    code.setText("");
                    address.setText("");
                    authorization = null;
                    minecraftLogin = MicrosoftBackgroundLogin.fromMicrosoftTokens(tokens);
                    minecraftLogin.performLogin(null, account -> {
                        if (!isCurrent(attempt)) {
                            return;
                        }
                        if (!account.isMicrosoft || account.isDemo()) {
                            fail(attempt, "Minecraft Java Edition을 보유한 계정이 필요합니다");
                            return;
                        }
                        PojavProfile.setCurrentProfile(this, account.username);
                        finish();
                    }, failure -> fail(attempt, "Xbox 또는 Minecraft 인증에 실패했습니다. 다시 시도해 주세요."));
                });
            } catch (CancellationException ignored) {
                // 취소와 화면 종료는 인증 실패로 표시하지 않는다.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                String message = "Microsoft 인증 요청에 실패했습니다. 네트워크를 확인하고 다시 시도해 주세요.";
                if (failure instanceof MicrosoftDeviceCodeLogin.AuthenticationException) {
                    String error = ((MicrosoftDeviceCodeLogin.AuthenticationException) failure).error;
                    if (error.equals("expired_token")) {
                        message = "코드가 만료됐습니다. 새 코드로 다시 시도해 주세요.";
                    } else if (error.equals("authorization_declined") || error.equals("access_denied")) {
                        message = "Microsoft 로그인이 거절됐습니다. 다시 시도할 수 있습니다.";
                    } else {
                        message = "Microsoft 인증 오류: " + error;
                    }
                }
                String result = message;
                runOnUiThread(() -> fail(attempt, result));
            }
        });
    }

    private boolean isCurrent(int attempt) {
        return generation == attempt && !isFinishing() && !isDestroyed();
    }

    private void fail(int attempt, String message) {
        if (!isCurrent(attempt)) {
            return;
        }
        authorization = null;
        code.setText("");
        address.setText("");
        status.setText(message);
        openBrowser.setEnabled(false);
        copyCode.setEnabled(false);
        retry.setEnabled(true);
    }

    private void cancelRequests() {
        generation++;
        if (deviceLogin != null) {
            deviceLogin.cancel();
        }
        if (minecraftLogin != null) {
            minecraftLogin.cancel();
        }
        if (request != null) {
            request.cancel(true);
        }
    }

    @Override
    protected void onDestroy() {
        cancelRequests();
        executor.shutdownNow();
        super.onDestroy();
    }

    private TextView text(LinearLayout parent, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setPadding(0, 12, 0, 12);
        parent.addView(view);
        return view;
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        parent.addView(button);
        return button;
    }
}
