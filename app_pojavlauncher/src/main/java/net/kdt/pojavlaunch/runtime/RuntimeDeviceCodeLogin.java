package net.kdt.pojavlaunch.runtime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftDeviceCodeLogin;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** 화면을 소유하지 않는 Device Code 및 Xbox/Minecraft 인증 요청. */
final class RuntimeDeviceCodeLogin implements AmethystRuntime.LoginRequest {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MicrosoftDeviceCodeLogin deviceLogin = new MicrosoftDeviceCodeLogin();
    private volatile AmethystRuntime.DeviceCodeListener listener;
    private volatile MicrosoftBackgroundLogin minecraftLogin;
    private volatile Future<?> task;
    private volatile boolean cancelled;
    // 완료 콜백은 메인 스레드에서 한 번만 전달한다.
    private boolean finished;

    RuntimeDeviceCodeLogin(Context context, AmethystRuntime.DeviceCodeListener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        task = PojavApplication.sExecutorService.submit(() -> {
            try {
                MicrosoftDeviceCodeLogin.Tokens tokens = deviceLogin.authenticate(code -> dispatch(callback -> {
                    callback.onCode(code.userCode, code.verificationUri, code.expiresInSeconds);
                }));
                dispatch(callback -> {
                    callback.onAuthenticating();
                    MicrosoftBackgroundLogin login = MicrosoftBackgroundLogin.fromMicrosoftTokens(tokens);
                    minecraftLogin = login;
                    if (cancelled) {
                        login.cancel();
                        return;
                    }
                    login.performLogin(null, account -> dispatch(completed -> {
                        if (!account.isMicrosoft || account.isDemo()) {
                            fail(new IllegalStateException("Minecraft Java Edition을 보유한 계정이 필요합니다"));
                            return;
                        }
                        PojavProfile.setCurrentProfile(context, account.username);
                        finished = true;
                        listener = null;
                        completed.onReady();
                    }), this::fail);
                });
            } catch (CancellationException ignored) {
                // 취소는 호스트의 명시적 동작이므로 실패 콜백을 전달하지 않는다.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception failure) {
                fail(failure);
            }
        });
    }

    private void dispatch(Consumer<AmethystRuntime.DeviceCodeListener> action) {
        main.post(() -> {
            AmethystRuntime.DeviceCodeListener callback = listener;
            if (!cancelled && !finished && callback != null) {
                action.accept(callback);
            }
        });
    }

    private void fail(Throwable failure) {
        dispatch(failed -> {
            finished = true;
            listener = null;
            failed.onFailure(failure);
        });
    }

    /** 호스트는 화면을 닫을 때 호출한다. 브라우저 전환만으로 취소할 필요는 없다. */
    @Override
    public void cancel() {
        cancelled = true;
        listener = null;
        deviceLogin.cancel();
        MicrosoftBackgroundLogin login = minecraftLogin;
        if (login != null) {
            login.cancel();
        }
        Future<?> request = task;
        if (request != null) {
            request.cancel(true);
        }
    }
}
