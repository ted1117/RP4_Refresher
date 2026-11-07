package com.hidsquid.refreshpaper;

import android.os.Bundle;

import android.app.Activity;

/**
 * 화면 새로고침용 외부 공개 API. Key Mapper 같은 키 매핑 앱이 부른다.
 * 앱 안에서는 쓰지 않는다. 지우거나 exported를 끄면 안 된다 (docs/external-api.md).
 *
 * onCreate에서 바로 finish()한다. 창이 열렸다 닫히면 e-ink 화면이 다시 그려진다.
 */
public class RefreshScreen extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
