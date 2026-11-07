# 외부 호출 API

Key Mapper 같은 키 매핑 앱이 화면을 새로고침할 때 부르는 진입점이다. 앱 안에서는 쓰지 않는다.

## 1. 새로고침 broadcast

- 이름: `com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN`
- 동작: 접근성 서비스가 받아 전체 새로고침(`doFullRefresh()`)을 한다. 잔상을 지우는 full refresh(SurfaceFlinger 로그 `setEPDFullRefresh 1`)라서 두 방법 중 이쪽을 권장한다.
- 요구사항: 접근성 서비스가 켜져 있어야 한다. 리시버를 서비스가 동적으로 등록하기 때문이다.

adb 예시:

```
adb shell am broadcast -a com.hidsquid.refreshpaper.ACTION_REFRESH_SCREEN
```

Key Mapper 설정: 동작 추가 → Send intent → Broadcast, Action에 위 이름을 넣는다.

## 2. RefreshScreen 액티비티

- 이름: `com.hidsquid.refreshpaper/.RefreshScreen` (exported, singleInstance)
- 동작: 열리자마자 닫힌다. 창이 열렸다 닫히면서 화면 전체가 다시 그려진다. 다만 일반 모드로 다시 그릴 뿐 full refresh 표시(`setEPDFullRefresh`)는 없어서, 잔상 제거는 broadcast보다 약할 수 있다.
- 요구사항: 없다. 접근성 서비스가 꺼져 있어도 동작한다.

adb 예시:

```
adb shell am start -n com.hidsquid.refreshpaper/.RefreshScreen
```

Key Mapper 설정: 동작 추가 → Launch activity에서 RefreshPaper의 RefreshScreen을 고른다.

## 변경 시 주의

- 이 둘을 지우거나 권한을 붙이지 말 것. 외부 앱이 부르는 API라 깨지면 사용자의 키 매핑이 멈춘다.
- `ACTION_REFRESH_SCREEN`은 #28에서 실수로 지웠다가 복구했다.
- 다른 내부 broadcast(스크린샷, 퀵 설정, 밝기)에 권한을 거는 작업(docs/TODO.md 참고)을 할 때도 이 둘은 열어 둔다.
