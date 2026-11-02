# JEI 19.70 compatibility — 2026-11-03

Issue [#30](https://github.com/StarskyXIII/Collapsible_Groups/issues/30). Local patched CG 2.0.0-beta5, based on `021bd1a` plus the working-tree compatibility fix.

JEI's optimized bytecode can reuse local slot 0 before returning. Instance Mixin callbacks at those returns can consequently fail verification and prevent the overlay from starting. The input wrapper now intercepts `InputArea.Builder.controls` in `createInputArea`; ingredient-source notifications mark pending changes at method entry. Foreground and tooltip callbacks cover every normal return so empty search results retain the manager button.

| Official JEI | Loader | In-game checks | Result |
| --- | --- | ---: | --- |
| 19.70.0.458 | NeoForge 21.1.249 | 14 | Passed |
| 19.70.0.460 | NeoForge 21.1.249 | 14 | Passed |
| 19.70.0.469 | NeoForge 21.1.249 | 14 | Passed |
| 19.70.0.469 | Fabric 0.19.5 | 14 | Passed |
| 19.70.0.469 | Forge 52.0.28 | 8 | Passed |

The Bridge cases cover editor saving, mixed item/fluid groups, expansion/collapse, positive and empty searches, manager entry from empty results, and clearing search. Forge's separate smoke probe covers expansion/collapse, search and manager entry. Every assertion requires three distinct frames and ticks. All clients exited normally; Bridge configurations were restored byte for byte. These focused checks exclude drag operations, cold-restart persistence, multiplayer and performance measurements.

All game runs above use the final default-built product JARs, retaining the minimum JEI version **19.69.0.456**. The official JEI JARs are unmodified. Packaged bytecode checks additionally passed for **all three loaders** against **each of .458, .460 and .469** (27 parameterized checks), including the callback receiver at injection sites and all render exits. The ABI matrix overrides only the test artifact resolution, preserving the product's minimum version.

Full default build and checks passed: 1,157 common, 3 Forge and 28 NeoForge JUnit tests, zero failures/errors/skips. Log: `build/issue30-469/verified-baseline-20261103.log`.

Final local evidence (repository-relative, Git-ignored):

- `.458`: `build/issue30-458/evidence/suite-2026-11-02T17-02-41-978Z/neo-jei/result.json`.
- `.460`: `build/issue30-460/evidence/suite-2026-11-02T17-04-10-644Z/neo-jei/result.json`.
- `.469` NeoForge/Fabric: `build/issue30-469/evidence/suite-2026-11-02T17-04-57-995Z/`.
- `.469` Forge: `build/issue30-smoke/evidence/forge-2026-11-02T17-06-39-397Z/`.
- ABI matrix: `build/issue30-469/abi-19.70.0.{458,460,469}-20261103.log`.

These are local verification results; no release was published.

---

# 1.21.1 beta Bridge 結果

2026-09-30（Asia/Taipei）。CG 2.0.0-beta3，原始碼基底 3cc6546 加上本次無效規則預覽修正。

| Profile | 遊戲檢查 | 圖像比對 | 結果 |
| --- | ---: | ---: | --- |
| neo-jei | 46 | 3 | 通過 |
| neo-emi | 46 | 3 | 通過 |
| 合計 | 92 | 6 | 通過 |

固定 Minecraft 1.21.1、NeoForge 21.1.249、JEI 19.42.0.379／EMI 1.1.24、Java 21、Bridge 1.0.0 build 171。兩個 profile 使用相同核心案例，數字包含跨 viewer 重複驗證。

最新完整證據：`evidence/suite-2026-09-30T06-34-31-588Z`；彙整：`evidence/final-verification.json`。完整本機證據受 Git 忽略。

- 各 40 項核心操作、混合成員、搜尋、修飾鍵／拖曳、規則、歸屬與冷啟動檢查。
- 各 6 項新版批次選取、停用／啟用、分類移動、側欄與冷啟動分類保存檢查。
- 各 3 項已檢視區域的圖像比對：混合群組管理器、無效規則預覽、分類移動後的管理器。
- Common JUnit：1,122 項，零失敗／錯誤；runner：13 項通過。
- NeoForge build、修正後的 common:test／NeoForge jar、Fabric／Forge compileJava 通過。
- 正常退出、程序結束、設定按原位元組還原、端口關閉及產物 SHA 核對通過。
- NeoForge 產品 jar 不含 Bridge／probe 類別。

未修正 beta 曾在 invalid-rule-retains-last-valid-preview 重現物品預覽遺失；修正後在兩個 viewer 通過。失敗、修正與產物雜湊詳見 [實作紀錄](../../docs/bridge-beta-regression-20260930.md)。

本次範圍不含 Fabric／Forge 的 beta 遊戲實跑，也不含 KubeJS／generic／中文矩陣及效能測量。1.5.0 的先前結果不能代替 beta 驗證。重跑方式見 [README.md](README.md)。

## Historical scope

The results above apply to the dated beta3 artifacts and pinned dependencies only. They do not certify the current checkout, beta5, or the updated JEI pins. Use the README to generate new evidence for the exact deployed artifacts.
