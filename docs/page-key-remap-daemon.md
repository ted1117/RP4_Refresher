# 페이지 키 리맵 데몬 (Magisk 모듈 실행)

## 목표

- 데몬은 앱이 아니라 Magisk 모듈의 `service.sh`가 root로 실행한다. 앱은 su를 쓰지 않는다.
- 앱은 상태 파일만 쓰고, 데몬은 그 파일을 감시해서 동작을 바꾼다.
- 앱이 죽으면 데몬은 치환을 끄고 `gpio_keys` 독점을 푼다.

## 저장소 구조

| 경로 | 내용 |
|---|---|
| `daemon/page_key_remapd.c` | 데몬 소스 (기존 `tools/page-key-remapd`에서 이동, 이제 git으로 관리) |
| `daemon/build.sh` | NDK로 arm64 빌드 → `magisk/module/bin/page_key_remapd` |
| `magisk/module/` | 모듈 템플릿 (`module.prop`, `customize.sh`, `service.sh`, `uninstall.sh`, privapp 권한 xml, `bin/sqlite3`, `META-INF`) |
| `magisk/build_module.sh <서명된 APK>` | 데몬 빌드 + APK 복사 + zip 생성 (`build/magisk/RefreshPaper-<version>.zip`) |

앱 APK에서는 `assets/bin`(데몬 바이너리)을 뺀다.

## 상태 파일 프로토콜 (버전 2)

- 위치: 기기 보호 저장소 `/data/user_de/0/com.hidsquid.refreshpaper/files/page_key_remap/state`
  - 앱: `createDeviceProtectedStorageContext().filesDir/page_key_remap/state`
  - 잠금 해제 전에도 읽을 수 있고, root 데몬이 읽을 수 있다.
- 쓰기: 같은 폴더의 `state.tmp`에 쓴 뒤 `state`로 이름 변경 (중간 상태를 읽지 않게).
- 내용: 공백으로 구분한 한 줄

```
2 <pid> <mode> <up_long> <down_long> <timeout_ms>
```

| 필드 | 의미 |
|---|---|
| `2` | 프로토콜 버전. 다르면 데몬은 꺼짐으로 취급 |
| `pid` | 상태를 쓴 앱 프로세스 번호 (`Process.myPid()`) |
| `mode` | 0 꺼짐, 1 볼륨 치환, 2 볼륨 치환+반전, 3 반전만 (기존과 같음) |
| `up_long`, `down_long` | 페이지 업/다운 길게 누르기 신호(F11/F12) 사용 여부, 0/1 |
| `timeout_ms` | 길게 누르기 판정 시간, 100~5000 밖이면 500 |

### 데몬이 "꺼짐"으로 취급하는 경우

- 파일이 없거나 형식이 틀림, 버전이 2가 아님
- `pid`가 살아 있지 않거나, `/proc/<pid>/cmdline`이 `com.hidsquid.refreshpaper`로 시작하지 않음 (pid 재사용 방지)

## 데몬 동작

- 시작 시 `gpio_keys`(PAGEUP/PAGEDOWN 보유)를 찾고, 가상 장치 `rp_page_key_remap`을 **한 번 만들어 계속 유지**한다. 앱은 이 장치가 있는지로 데몬 생존을 판단한다.
- 상태 폴더를 inotify로 감시한다 (`IN_CLOSE_WRITE | IN_MOVED_TO`, 파일 이름 `state`만). 폴더가 아직 없으면 5초마다 다시 시도한다.
- 독점이 필요한 조건: 유효한 상태이고 `mode != 0` 또는 길게 누르기 중 하나라도 켜짐.
- 원본 장치는 독점 여부와 관계없이 항상 열어 두고 이벤트를 읽어서 **실제로 눌린 키**를 추적한다.
- 독점 걸기/풀기는 실제로 눌린 키가 없고 길게 누르기 대기 중이 아닐 때만 한다. 그 전까지는 미룬다.
- 독점을 풀기 전에 가상 장치에서 눌린 상태로 남은 키를 모두 뗀다.
- 키를 누를 때 내보낸 코드를 키별로 기억했다가, 반복(2)과 뗌(0)은 같은 코드로 내보낸다. 누른 채 상태가 바뀌어도 짝이 어긋나지 않는다.
- 독점 중 키를 누를 때마다 앱 pid 생존을 확인한다. 독점 중에는 5초마다도 확인한다.
- 독점 실패(다른 프로세스가 이미 독점, 예: Key Mapper)는 종료하지 않고 로그를 남긴 뒤 5초 뒤 다시 시도한다.
- SIGTERM/SIGINT: 눌린 키 정리 → 독점 해제 → 가상 장치 제거 → 종료.
- 로그: 상태 변화와 오류만 stderr로 한 줄씩 (시작, 원본 장치, 독점/해제, 상태 변경, 오류).

## service.sh

- 예전 방식(앱이 `/data/local/tmp`에서 띄운 데몬)이 남아 있으면 종료하고 파일을 지운다.
- `sys.boot_completed`를 기다린 뒤 감시 루프를 돈다.
  - `$MODDIR/bin/page_key_remapd --state-dir <위 폴더>` 실행, 출력은 `/data/local/tmp/page_key_remapd.log`
  - 종료되면 종료 코드를 로그에 남기고 재시작. 60초 안에 죽으면 대기 시간을 1→2→4…최대 300초로 늘리고, 60초 이상 살았으면 1초로 되돌린다.
- 로그 파일은 부팅 때 1MB가 넘으면 비운다.

## 앱

- `PageKeyRemapDaemonController`: su·바이너리 복사·데몬 시작/종료 코드를 지우고, 상태 계산과 상태 파일 쓰기만 남긴다. 서비스 종료 시에는 모드 0, 길게 누르기 0으로 쓴다.
- 데몬 상태 확인: `InputManager`의 입력 장치 중 이름이 `rp_page_key_remap`인 것이 있는지.
- 실험실 화면과 버튼 동작 설정 화면: 페이지 키 기능을 하나라도 쓰는데 데몬이 없으면 "Magisk 모듈을 확인하라"는 안내를 보여 준다.

## 범위 밖 (다음 작업)

- `MainPermissionCoordinator`의 su(접근성 자동 켜기), `EPDDisplayModeController`의 su 폴백, LSPosed 매니저 열기의 su 정리
