# TODO

## 버그·안정성

### hook·리시버 (한 PR로 묶기 좋음)

- [x] SystemUI hook 예외 처리: `SystemUIHook`의 `getMethod("isForegroundApp")`가 예외 처리 밖에 있고 `findViewById` 결과에 null 확인이 없음. RIDI 펌웨어가 바뀌면 SystemUI가 반복 재시작될 수 있음 (#29)
- [ ] broadcast 리시버 권한: `ACTION_TAKE_SCREENSHOT`, `ACTION_OPEN_QUICK_SETTINGS`, `ACTION_SHOW_BRIGHTNESS_ACTIVITY` 리시버가 권한 없이 등록되어 다른 앱이 실행시킬 수 있음. 단, `ACTION_REFRESH_SCREEN`은 외부 공개 API라 열어 둘 것 (docs/external-api.md)
- [x] 터치 리프레시 hook 중복: `AutoRefreshHook`의 `loadApp {}`가 한 프로세스에서 여러 번 걸리면 터치 1번이 2번으로 집계됨 — hook 제거로 해결 (#28)
- [x] 터치 리프레시 설정 읽기: 터치할 때마다 `HookPrefs`가 설정 파일을 다시 읽음 — hook 제거로 해결 (#28)

### 그 밖

- [x] system_server hook 설정 읽기: `SystemServerHook`의 `isSecureLocked` hook이 WM 전역 락 안에서 250ms마다 설정 파일을 다시 읽음. 메모리에 두고 변경 시에만 다시 읽기 (#29)
- [x] `PageCountDialogController`가 접근성 서비스를 `startService`로도 시작함. 접근성이 꺼져 있으면 시스템과 연결되지 않은 서비스 인스턴스가 생김 — `startService`를 없애고 설정 변경 broadcast로 개수를 다시 읽음 (켜진 상태에서 부르면 접근성을 꺼도 서비스·데몬 설정이 남는 것도 기기에서 확인) (#32)
- [x] 스크린샷 토스트: 저장 실패·취소 때도 "스크린샷 촬영" 토스트가 뜸 — 저장 결과(`errorMsgResId`)를 보고 성공일 때만 띄움 (#31)
- [ ] 데몬: 키를 누른 채 모드가 바뀌어 두 물리 키가 같은 출력 키가 되면, 먼저 뗀 키가 다른 키의 출력까지 뗌. 출력 키별로 누른 물리 키 수 세기 (알려진 한계, 낮음)

## 진단 대기

- [ ] 전원키가 가끔 동작하지 않음: 기기에 키 로거(`/data/adb/service.d/rp_keylog.sh`, 로그 `/data/local/tmp/rp_keylog/`)가 기록 중. 증상 시각과 종류(절전 진입 실패 / 깨어남 실패)를 받아 분석

## 기능 아이디어

- [x] 터치 리프레시를 LSPosed scope 없이 데몬에서 처리 (#28)
- [ ] 앱별 리프레시 주기

## 코드 정리

- [x] 중복 상수 정리: broadcast action 문자열(`ACTION_OPEN_QUICK_SETTINGS`가 `SystemUIHook`·`KeyInputDetectingService`에 각각 정의), 기본 런처 컴포넌트(`SystemUIHook`·`SettingsRepository`), `"com.ridi.paper"` 반복 — `SharedConstants`로 모음 (#33)
- [x] `DeviceSecurityController`가 `rp_prefs`를 따로 여는 부분을 `SettingsRepository`로 합치기 — `DeviceSecurityController` 삭제 (#33)
- [x] F1 동작 상수를 enum/sealed class로 (`getF1Action()`의 `when`과 `VALID_F1_ACTIONS` 이중 관리) — `ButtonAction` enum, 저장 값(0~7)은 그대로 (#33)
- [ ] 사용하지 않는 코드 제거 (`OverlayController.poke()`, `StackBarView.setBarCount()`, `layout_permission_state.xml`의 `android:onClick="onClick"`)
- [x] 사용하지 않는 리소스 제거 (lint `UnusedResources` 29개. `ic_launcher` 계열은 앱 아이콘 연결 확인 후) — 지운 drawable만 쓰던 dimen 2개까지 31개, lint `UnusedResources` 0개 (#33)
- [x] 선택 대화상자 중복: 페이지 수·F1 동작 대화상자의 항목·체크 표시 수동 연결을 공통 헬퍼로 — XML 레이아웃은 그대로 두고 연결 코드만 `ChoiceDialog`로 합침(5개 대화상자). 기기 스크린샷 픽셀 일치 (#33)
- [ ] 단위 테스트
  - [x] `ForegroundAppTracker`, `ButtonAction`(저장 id 0~7 고정) JVM 단위 테스트, 템플릿 `ExampleUnitTest`·`ExampleInstrumentedTest` 삭제 (#34)
  - [ ] 데몬 키 치환·길게 누르기 판정 (C 코드라 판정 로직을 입력·파일 처리에서 분리해야 테스트 가능)
  - [ ] `KeyShortcutHandler` 페이지 수 카운트 (`SettingsRepository`에 직접 의존해 그대로는 테스트 불가)

## 의존성

- [ ] YukiHookAPI 1.3.1 → YukiHook 1.5.x 마이그레이션 (급하지 않음, 1.5.0은 빌드가 깨지는 변경)
  - [ ] 의존성 교체: `api` → `yukihook-core`, `ksp-xposed` → `yukihook-compiler`, `yukihook-runtime-xposed82` 추가(Xposed API 82는 `compileOnly` 유지), KavaRef를 쓰면 `yukihook-extension-kavaref`
  - [ ] Kotlin/KSP 업그레이드: 1.5.0이 Kotlin 2.4.21로 빌드되어 현재 2.2.10으로는 못 읽을 가능성이 큼 (최소 2.3, 사실상 2.4대)
  - [x] 반사 API(`method {}`, `field {}`) 제거됨 → KavaRef로 재작성: `SystemServerHook`, `SystemUIHook`, `ShutdownTimerValidatorHook` — 1.3.1에서 선반영, 1.5에서는 import만 extension-kavaref로 변경 (#29)
  - [ ] `HookEntry`: `@InjectYukiHookWithXposed`/`IYukiHookXposedInit` → `@YukiHookXposed82Entry`/`YukiHookXposedModule`(object), `configs { debugLog }` → `YukiHook.configure { logging }`
  - [ ] 소스의 `app/src/main/assets/xposed_init`, `app/src/main/resources/META-INF/yukihookapi_init` 삭제 (있으면 빌드 거부)
  - [ ] 매니페스트 `xposedminversion=93`에 `tools:replace="android:value"` 추가 (런타임 모듈이 82를 선언, `xposedsharedprefs`에 93 필요)
  - [ ] KavaRef 버전 정리 (직접 지정 1.0.2 vs BOM 1.1.1), `appContext` → `hostApplication` 등 지원 중단 API 교체
  - [ ] 기기 확인: 특히 system_server 안의 SettingsProvider hook(`loadApp("com.android.providers.settings")`)이 1.5.0의 `loadApp` 변경 후에도 걸리는지
  - [ ] (선택) `yukihook-runtime-libxposed`로 원격 `SharedPreferences`를 쓰면 `HookPrefs`의 파일 재읽기를 없앨 수 있음. 지금은 system_server hook이 `XSharedPreferences` 변경 리스너 + 30초 안전망(파일 stat만)으로, SystemUI hook이 값을 읽을 때마다(최소 250ms 간격) 파일을 다시 읽음 — LSPosed의 libxposed API 지원 여부 먼저 확인

## 기기·환경 정리

- [ ] 기기의 앱 데이터 백업 `/data/local/tmp/rp_backup_20261102_005800/` 삭제
- [ ] 키스토어 `~/KeyStore/RefreshPaper` 백업 (잃어버리면 같은 키로 업데이트 불가)
- [ ] 전원키 문제 진단이 끝나면 키 로거 제거 (`/data/adb/service.d/rp_keylog.sh` 삭제 후 재부팅)
