# TODO

## 버그·안정성

### hook·리시버 (한 PR로 묶기 좋음)

- [ ] SystemUI hook 예외 처리: `SystemUIHook`의 `getMethod("isForegroundApp")`가 예외 처리 밖에 있고 `findViewById` 결과에 null 확인이 없음. RIDI 펌웨어가 바뀌면 SystemUI가 반복 재시작될 수 있음
- [ ] broadcast 리시버 권한: `ACTION_TAKE_SCREENSHOT`, `ACTION_OPEN_QUICK_SETTINGS`, `ACTION_SHOW_BRIGHTNESS_ACTIVITY` 리시버가 권한 없이 등록되어 다른 앱이 실행시킬 수 있음. `ACTION_REFRESH_SCREEN`은 hook한 리더 앱이 보내야 하므로 제외
- [ ] 터치 리프레시 hook 중복: `AutoRefreshHook`의 `loadApp {}`가 한 프로세스에서 여러 번 걸리면 터치 1번이 2번으로 집계됨. `isFirstApplication`으로 한 번만 걸기
- [ ] 터치 리프레시 설정 읽기: 터치할 때마다 `HookPrefs`가 설정 파일을 다시 읽음 (리더 앱 메인 스레드 디스크 I/O)

### 그 밖

- [ ] system_server hook 설정 읽기: `SystemServerHook`의 `isSecureLocked` hook이 WM 전역 락 안에서 250ms마다 설정 파일을 다시 읽음. 메모리에 두고 변경 시에만 다시 읽기
- [ ] `PageCountDialogController`가 접근성 서비스를 `startService`로도 시작함. 접근성이 꺼져 있으면 시스템과 연결되지 않은 서비스 인스턴스가 생김
- [ ] 스크린샷 토스트: 저장 실패·취소 때도 "스크린샷 촬영" 토스트가 뜸
- [ ] 데몬: 키를 누른 채 모드가 바뀌어 두 물리 키가 같은 출력 키가 되면, 먼저 뗀 키가 다른 키의 출력까지 뗌. 출력 키별로 누른 물리 키 수 세기 (알려진 한계, 낮음)

## 진단 대기

- [ ] 전원키가 가끔 동작하지 않음: 기기에 키 로거(`/data/adb/service.d/rp_keylog.sh`, 로그 `/data/local/tmp/rp_keylog/`)가 기록 중. 증상 시각과 종류(절전 진입 실패 / 깨어남 실패)를 받아 분석

## 기능 아이디어

- [ ] 터치 리프레시를 LSPosed scope 없이 데몬에서 처리 (터치스크린 입력을 직접 읽어 탭 횟수 집계)
- [ ] 앱별 리프레시 주기
- [ ] F1 동시 누르기 단축키 (Key Mapper 없이 LSPosed·Magisk 열기)
- [ ] 보안 화면 우회 설정에 "앱을 다시 열어야 적용됨" 안내

## 코드 정리

- [ ] 중복 상수 정리: broadcast action 문자열(`HookEntry`·`KeyInputDetectingService`), 기본 런처 컴포넌트(`SystemUIHook`·`SettingsRepository`), `"com.ridi.paper"` 반복
- [ ] `DeviceSecurityController`가 `rp_prefs`를 따로 여는 부분을 `SettingsRepository`로 합치기
- [ ] F1 동작 상수를 enum/sealed class로 (`getF1Action()`의 `when`과 `VALID_F1_ACTIONS` 이중 관리)
- [ ] 사용하지 않는 코드 제거 (`OverlayController.poke()` 등)
- [ ] 단위 테스트: 데몬 키 치환·길게 누르기 판정, `ForegroundAppTracker`

## 기기·환경 정리

- [ ] 기기의 앱 데이터 백업 `/data/local/tmp/rp_backup_20261102_005800/` 삭제
- [ ] 키스토어 `~/KeyStore/RefreshPaper` 백업 (잃어버리면 같은 키로 업데이트 불가)
- [ ] 전원키 문제 진단이 끝나면 키 로거 제거 (`/data/adb/service.d/rp_keylog.sh` 삭제 후 재부팅)
