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

## 상태 파일 프로토콜 (버전 3)

- 위치: 기기 보호 저장소 `/data/user_de/0/com.hidsquid.refreshpaper/files/page_key_remap/state`
  - 앱: `createDeviceProtectedStorageContext().filesDir/page_key_remap/state`
  - 잠금 해제 전에도 읽을 수 있고, root 데몬이 읽을 수 있다.
- 쓰기: 같은 폴더의 `state.tmp`에 쓴 뒤 `state`로 이름 변경 (중간 상태를 읽지 않게).
- 내용: 공백으로 구분한 한 줄

```
3 <pid> <mode> <up_long> <down_long> <timeout_ms> <touch_trigger>
```

| 필드 | 의미 |
|---|---|
| `3` | 프로토콜 버전. 다르면 데몬은 꺼짐으로 취급 |
| `pid` | 상태를 쓴 앱 프로세스 번호 (`Process.myPid()`) |
| `mode` | 0 꺼짐, 1 볼륨 치환, 2 볼륨 치환+반전, 3 반전만 (기존과 같음) |
| `up_long`, `down_long` | 페이지 업/다운 길게 누르기 신호(F11/F12) 사용 여부, 0/1 |
| `timeout_ms` | 길게 누르기 판정 시간, 100~5000 밖이면 500 |
| `touch_trigger` | 터치 리프레시 횟수. 0 꺼짐, 1~1000이면 N번 터치마다 신호, 범위 밖이면 0 |

### 데몬이 "꺼짐"으로 취급하는 경우

- 파일이 없거나 형식이 틀림, 버전이 3이 아님
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

## 터치 리프레시 (데몬)

- 유효한 상태에서 `touch_trigger > 0`일 때만 터치스크린을 연다. 0이 되면 닫는다 (대상이 아닌 앱에서는 비용 0). 상태가 바뀔 때마다(파일 다시 읽기, pid 확인) 다시 판단한다. 터치스크린이 열려 있는 동안은 틱(주기 점검)마다 상태 파일과 pid를 다시 확인하고, 신호를 내기 직전에도 pid를 확인해 앱이 죽었으면 신호를 내지 않는다.
- 터치스크린 찾기: `/dev/input/event*` 중 `INPUT_PROP_DIRECT`, `EV_KEY BTN_TOUCH`, `EV_ABS ABS_MT_POSITION_X`를 모두 가진 장치. `gpio_keys`와 우리 가상 장치(`rp_page_key_remap`)는 제외한다 (이 기기에서는 "Elan Touchscreen"). `O_RDONLY | O_NONBLOCK`으로 열고 `EVIOCSCLOCKID`를 `CLOCK_MONOTONIC`으로 맞추며(실패하면 수신 시각 사용), `EVIOCGRAB`은 절대 하지 않는다.
- 못 찾거나 못 열면 한 번만 로그를 남기고, 필요한 동안 5초 틱에서 다시 시도한다. 터치 장치 오류(`POLLERR`/`POLLHUP`/`POLLNVAL`, 읽기 실패)는 로그 후 닫고 틱에서 재시도하며 데몬은 종료하지 않는다.
- 세기: `BTN_TOUCH` 1에서 이벤트 시각을 기록하고, 0에서 기록이 있고 500ms 이내면 한 번으로 센다. 길게 누른 것은 세지 않는다. 좌표는 읽지 않고 다른 이벤트는 무시한다.
- 횟수가 `touch_trigger`에 닿으면 가상 장치로 `KEY_F10`을 한 번 누르고 떼고(각각 SYN) 횟수를 0으로 되돌린다. 앱의 접근성 서비스가 이 `KEY_F10`을 받아 e-ink 화면을 새로 고친다.
- 카운터는 터치 장치를 닫거나 `touch_trigger`가 0이 되어도 유지한다(설정 화면에 다녀와도 이어서 센다). 0이 아닌 값에서 다른 0이 아닌 값으로 바뀔 때만 0으로 되돌린다.
- 장치를 열었을 때 이미 `BTN_TOUCH`가 눌려 있으면 그 터치는 down 기록이 없으므로 세지 않는다.
- `SYN_DROPPED`는 원본 장치와 같이 다음 `SYN_REPORT`까지 버리고, 기록된 눌림을 지운다.
- 로그는 열기/닫기/실패와 상태 변경만 남기고, 터치나 신호마다는 남기지 않는다.

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

## LSPosed 매니저 열기 요청

LSPosed 매니저는 root/shell만 띄울 수 있어서, 앱이 요청 파일을 남기고 모듈(`service.sh`)이 대신 연다.

- 요청 폴더: `/data/user_de/0/com.hidsquid.refreshpaper/files` (앱의 device-protected `files/` 자체. 접근성 서비스가 page-key 상태를 쓰면 이미 존재한다. 앱도 방어적으로 `mkdirs()` 한다. 예전의 전용 하위 폴더 `root_request`는 첫 요청 전에 없어서 감시가 첫 요청을 놓쳤기 때문에 쓰지 않고, 모듈이 남아 있으면 한 번 지운다.)
- 앱: `launch_lsposed.tmp`에 쓴 뒤 `launch_lsposed`로 이름 변경.
- 모듈: `launch_lsposed`가 생기면 **먼저 지운다** (앱은 3초 안에 사라지면 처리된 것으로 본다). 그다음 `am start -c org.lsposed.manager.LAUNCH_MANAGER com.android.shell/.BugreportWarningActivity`를 실행하고, 출력과 종료 코드를 `/data/local/tmp/page_key_remapd.log`에 남긴다.
- 감시: 별도 백그라운드 루프가 `inotifyd`로 폴더를 본다. 폴더가 없으면 1초마다 재시도하고, `inotifyd`가 끝나면(폴더 삭제 등) 1초 뒤 다시 시작한다. 일부 toybox `inotifyd`는 마스크 첫 글자를 무시하고 옮겨 들어옴/나감(`m`/`y`) 글자도 뒤바뀌어(`m`에 옛 이름 `.tmp`, `y`에 새 이름) 와서, 마스크는 `wwmy`를 쓰고 이벤트의 종류·이름은 믿지 않는다. 어떤 이벤트든 `launch_lsposed`가 실제로 있을 때만 처리하므로 `.tmp`만 있을 때는 아무 일도 하지 않는다. `files/`에 건 inotify는 `page_key_remap/` 하위 폴더 안의 이벤트는 알려주지 않지만(하위 폴더 항목 자체의 이벤트만 온다), 존재 여부만 확인하므로 무해하다.
- 루프 시작 때 이미 있는 요청 파일은 수정 시각이 10초 이내면 처리하고, 더 오래됐으면 재부팅 전에 남은 것으로 보고 실행하지 않고 지운다.

## 범위 밖

- 접근성 자동 켜기와 EPD 모드 변경의 su는 제거했다. LSPosed 매니저 열기는 위의 모듈 요청 방식으로 처리한다.
