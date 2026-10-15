package net.kdt.pojavlaunch.runtime;

import android.net.Uri;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;

/** 라이브러리의 계정 로그인 화면이며 호스트의 서비스 정책을 포함하지 않는다. */
public final class RuntimeAccountActivity extends AppCompatActivity {
    private static final int CONTENT_ID = 0x4a010001;
    private boolean authenticating;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        FrameLayout content = new FrameLayout(this);
        content.setId(CONTENT_ID);
        setContentView(content);
        if (savedState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(CONTENT_ID, new MicrosoftLoginFragment())
                    .commit();
        }
    }

    public void completeAuthorization(Uri uri) {
        if (authenticating) return;
        String code = uri.getQueryParameter("code");
        if (code == null || code.isEmpty()) {
            Toast.makeText(this, "로그인 응답에 인증 코드가 없습니다", Toast.LENGTH_LONG).show();
            return;
        }
        authenticating = true;
        new MicrosoftBackgroundLogin(false, code).performLogin(null, account -> {
            PojavProfile.setCurrentProfile(this, account.username);
            finish();
        }, failure -> {
            authenticating = false;
            Toast.makeText(this, "Minecraft 로그인에 실패했습니다", Toast.LENGTH_LONG).show();
        });
    }
}
