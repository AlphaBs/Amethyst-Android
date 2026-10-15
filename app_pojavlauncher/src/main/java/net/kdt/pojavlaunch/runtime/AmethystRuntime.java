package net.kdt.pojavlaunch.runtime;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

/** 호스트 앱과 Android 게임 실행 구현 사이의 공개 경계. */
public final class AmethystRuntime {
    public interface Completion {
        void onReady();
        void onFailure(Throwable failure);
    }

    private AmethystRuntime() {}

    public static void initialize(Context context) {
        if (LauncherPreferences.DEFAULT_PREF == null) {
            LauncherPreferences.DEFAULT_PREF = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(context);
        }
        LauncherPreferences.loadPreferences(context);
        new File(Tools.DIR_ACCOUNT_NEW).mkdirs();
    }

    public static String accountName(Context context) {
        initialize(context);
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        return account == null || !account.isMicrosoft || account.isDemo() ? null : account.username;
    }

    public static String offlineAccountName(Context context) {
        initialize(context);
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(context, null);
        return account != null && account.isLocal() ? account.username : null;
    }

    public static void createOfflineAccount(Context context, String name) throws java.io.IOException {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Invalid offline player name");
        }
        initialize(context);
        MinecraftAccount account = new MinecraftAccount();
        account.username = name;
        account.profileId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name)
                .getBytes(StandardCharsets.UTF_8)).toString();
        account.save();
        PojavProfile.setCurrentProfile(context, name);
    }

    public static void openAccount(Activity activity) {
        activity.startActivity(new Intent(activity, RuntimeAccountActivity.class));
    }

    public static void refreshAccount(Activity activity, Completion completion) {
        initialize(activity);
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(activity, null);
        if (account == null || !account.isMicrosoft || account.isDemo()) {
            completion.onFailure(new IllegalStateException("Minecraft 로그인이 필요합니다"));
            return;
        }
        if (account.expiresAt > System.currentTimeMillis() + 60000) {
            completion.onReady();
            return;
        }
        new MicrosoftBackgroundLogin(true, account.msaRefreshToken).performLogin(null,
                updated -> completion.onReady(), completion::onFailure);
    }

    public static void prepareGame(Activity activity, File directory, String version,
                                   String renderer, int memoryMb, String[] gameArguments, Completion completion) {
        try {
            initialize(activity);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IllegalStateException("Cannot create game directory");
            }
            activity.getSharedPreferences("amethyst_runtime", Context.MODE_PRIVATE).edit()
                    .putString("gameDirectory", directory.getAbsolutePath())
                    .commit();
            LauncherPreferences.loadPreferences(activity);
            LauncherProfiles.load();
            String profileId = UUID.nameUUIDFromBytes(directory.getAbsolutePath()
                    .getBytes(StandardCharsets.UTF_8)).toString();
            MinecraftProfile profile = new MinecraftProfile();
            profile.name = directory.getName();
            profile.gameDir = directory.getAbsolutePath();
            profile.lastVersionId = version;
            profile.pojavRendererName = renderer;
            profile.extraGameArguments = gameArguments == null ? null : gameArguments.clone();
            LauncherProfiles.mainProfileJson.profiles.put(profileId, profile);
            LauncherProfiles.write();
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileId)
                    .putInt("allocation", memoryMb)
                    .commit();
            LauncherPreferences.loadPreferences(activity);
            PojavApplication.sExecutorService.execute(() -> {
                try {
                    AsyncAssetManager.prepareForHost(activity);
                    new AsyncVersionList().getVersionList(versions -> {
                        if (versions == null) {
                            completion.onFailure(new IllegalStateException("Cannot load Minecraft versions"));
                            return;
                        }
                        ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
                        new MinecraftDownloader().startForHost(activity, version,
                                new AsyncMinecraftDownloader.DoneListener() {
                            @Override public void onDownloadDone() { completion.onReady(); }
                            @Override public void onDownloadFailed(Throwable failure) {
                                completion.onFailure(failure);
                            }
                        });
                    }, false);
                } catch (Throwable failure) {
                    completion.onFailure(failure);
                }
            });
        } catch (Throwable failure) {
            completion.onFailure(failure);
        }
    }

    public static java.util.Map<String, String> renderers(Context context) {
        initialize(context);
        Tools.RenderersList available = Tools.getCompatibleRenderers(context);
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        for (int index = 0; index < available.rendererIds.size(); index++) {
            result.put(available.rendererIds.get(index), available.rendererDisplayNames[index]);
        }
        return result;
    }

    public static boolean isGameRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
        if (processes == null) return false;
        for (ActivityManager.RunningAppProcessInfo process : processes) {
            if (process.processName.equals(context.getPackageName() + ":game")) return true;
        }
        return false;
    }

    public static void launchGame(Activity activity) {
        activity.startActivity(new Intent(activity, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
    }
}
