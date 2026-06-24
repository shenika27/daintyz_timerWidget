# 데드코드 정리 TODO (B 그룹 — 보류)

> 2026-06-24 검토. **A 그룹(미리보기/상세 Compose 전환 고아)은 정리 완료.**
> 아래 **B 그룹은 상점 쪽 작업이 끝난 뒤 검토**한다 — 일부는 아직 안 만든 화면용 placeholder일 수 있어 섣불리 삭제 금지.

판별법: `grep -rn "R.string.<name>\b\|@string/<name>\b" app/src/main/java app/src/main/res` 로 참조 0인지 재확인 후 삭제.

## 미사용 문자열 (참조 0, 2026-06-24 기준)

이전 단계(앱 셸 XML→Compose 전환)에서 누적된 잔여 + 미래 placeholder 혼재.

- 위젯/기호: `timer_complete_label`, `sym_minus`, `sym_plus`, `sym_play`, `sym_pause`, `sym_stop`
- 옛 메인/설정 XML: `main_title`, `label_state`, `label_remaining`, `label_step`, `label_layout_mode`, `layout_top`, `layout_bottom`, `btn_save_step`, `btn_open_skins`, `btn_open_store`, `hint_step`
- 옛 스킨 선택 XML: `skin_select_title`, `skin_tab_character`, `skin_tab_timer`, `skin_badge_locked`, `skin_badge_selected`, `skin_btn_select`, `skin_apply_empty`
- 옛 상점/상세 XML: `store_title`, `store_set_label`, `skin_tag_prestige` ※ store_set_label·skin_tag_prestige 는 향후 상점/프리스티지 화면 placeholder 가능 → **상점 작업 시 사용 여부 확정**
- 옛 보유(Vault) XML: `cd_preview_widget`, `vault_empty`, `cd_vault_card`, `vault_page_title`, `vault_filter_all`, `vault_filter_owned`, `vault_filter_locked`

## 미사용 drawable (참조 0)

- 옛 Vault XML 카드/배지/필터: `vault_badge_bg`, `vault_card_bg`, `vault_filter_toggle_bg`, `vault_lock_overlay`, `vault_price_bg`

## 보류 (삭제 주의)

- `ic_launcher_foreground` — 참조 0이지만 런처 아이콘 관련. 적응형 아이콘 구성 확인 전 삭제 금지.

---

### 참고: 코드 레벨은 클린
import/함수/위젯 렌더링에는 데드코드 없음. 데드코드는 리소스(문자열/drawable)에만 잔존하며 대부분 셸 마이그레이션 누적분이다.
