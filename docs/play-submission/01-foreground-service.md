# 포그라운드 서비스(specialUse) 선언 자료

Play Console → **앱 콘텐츠 → 포그라운드 서비스 권한**에서 요구하는 답변 초안.
권한: `FOREGROUND_SERVICE_SPECIAL_USE` (subtype `character_timer_animation`).

---

## 사용 사례 (한국어 — 콘솔 설명란)

사용자가 직접 시작한 **카운트다운 타이머**의 진행과 완료를 추적합니다. 타이머가
도는 동안:
- 홈 화면 위젯의 캐릭터 애니메이션과 남은 시간을 실시간으로 갱신하고,
- "타이머 진행 중"을 알리는 상시 알림을 표시하며,
- 종료 시점에 완료 알림/효과를 정확히 트리거합니다.

이 동작은 **사용자가 명시적으로 타이머를 시작했을 때만** 실행되며, 타이머가
정지/완료되면 즉시 종료됩니다.

## 표준 FGS 타입을 쓰지 않는 이유 (영문 — 리뷰어용)

> The app runs a user-initiated countdown timer that drives a home-screen
> widget's character animation and shows ongoing progress in a persistent
> notification. None of the standard foreground service types fit a
> general-purpose countdown timer: it is not media playback, location,
> data sync, phone call, or connected-device work. `shortService` is limited
> to ~3 minutes and cannot cover typical timer durations (e.g. 25-minute
> focus sessions). The service starts only on explicit user action and stops
> as soon as the timer is stopped or completes.

## 사용자 혜택

타이머가 백그라운드/화면 꺼짐 상태에서도 정확히 진행·완료되고, 위젯의 캐릭터가
끊김 없이 동작합니다.

---

## 데모 영상 (요구될 수 있음)
Play가 specialUse는 데모 영상을 요구하는 경우가 잦습니다. 준비물:
1. 위젯에서 타이머 시작 → 상시 알림 표시되는 장면
2. 화면 꺼졌다 켜도 타이머가 정확히 진행되는 장면
3. 완료 시 알림/효과 트리거 장면
4. 타이머 정지 시 알림/서비스가 사라지는 장면 (= 상시 실행 아님을 증명)

## ⚠️ 리스크 메모
Play는 specialUse를 엄격히 심사하며, "표준 타입으로 충분하다"고 판단하면 반려할
수 있습니다. 위 "표준 타입 부적합" 논거 + 데모 영상이 핵심 방어 자료입니다.
반려 시 대안: 타이머 동작을 `shortService` 묶음 + 정확 알람(AlarmManager) 조합으로
재설계하는 방안 검토(긴 타이머는 알람으로 복구).
