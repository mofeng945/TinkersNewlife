# 给 AI 代理的工作规则（活文件，随用户口径更新）

> 这个文件是**用户定的硬规矩**，不是建议。新会话请先读这里，再读 `docs/开发备忘录.md`（很大，用 grep 搜关键词）。

## 0. 最重要：别轻易跑子代理
- **除非是"大量工作"（大批量机械改动、跨很多文件的审计、需要独立上下文的长链条排查），否则一律自己直接做**。
- 小改一行、加个翻译键、调个数值、改一段文案 —— **不要**派子代理。
- 子代理的代价：要多一轮往返、它看不到本会话上下文（容易跑偏）、验证成本高。
- 派子代理时**必须**在提示里给全：仓库铁律、部署口径、验证步骤、以及"不许改用户手绘贴图"这条。

## 1. 绝对不许碰用户的画
- `src/main/resources/assets/**/textures/**` 与 `models/**` 下的**既有文件一律不许覆盖**（用户手绘，覆盖过一次是重大事故，见备忘录 §390）。
- 需要占位/试验图时：**写到新路径**，或只改**引用它的 JSON**。

## 2. 每次改动都要走的流程
1. `powershell -ExecutionPolicy Bypass -File tools\check-json.ps1` → 必须 **bad 0**
2. `cmd /c ".\gradlew build --console=plain"` → **BUILD SUCCESSFUL**
3. 部署（见第 3 条）
4. 在 `docs/开发备忘录.md` **末尾**追加一节（节号接最后一节；旧节不要改，以新节为准）
5. `git add -A && git commit && git push`（push 往 stderr 写进度 ⇒ PowerShell 报 exit 1 属正常，看 `main -> main`）

## 3. 部署口径（很容易踩）
- `tools\deploy.ps1` 是**硬编码两个目标**（测试包 + NL 包），且**检测到任何 Minecraft 在跑就全部拒绝** ⇒ 覆盖运行中的 jar 会 `NoClassDefFoundError`。
- **用户要求"只装测试包"时**：不要用那个脚本 ⇒ 手动把 `build\libs\tinkersnewlife-1.0.1.13.jar` 拷到
  `G:\tex\.minecraft\versions\1.20.1-Forge_47.4.22\mods\` ⇒ 并核对 **两边 MD5 一致**。
- **不要动正在运行的那个实例**（用进程 `gameDir` 判断是哪个整合包）；也**不要**擅自改用户的 config 文件。

## 4. JSON / 文案
- 往 JSON 插块：**最后一行必须带逗号**（插在末尾则前一行补逗号、最后一行不带逗号）；插完立刻 `check-json`。
- 中文 `.ps1` 要 **UTF-8 带 BOM**（本机 `powershell` 是 5.1，否则按 ANSI 读会语法错）；读写 JSON 一律显式 `UTF8`。
- 新特性"出厂三件套"：`modifier.<id>` + `.description`（灰）+ `.flavor`（斜体）+ `.tip`（带 § 颜色码）+ `assets/tinkersnewlife/mantle/colors.json` 里一行颜色 + 手册条目。

## 5. 汇报口径
- **不许含糊、不许谎报**：没实机验证就说"未实机验证"；根因没 100% 确证就说"未确证"并给最可能解释与已采取的稳健措施。
- 改了哪些文件、验证结果（check-json 数字 / BUILD / jar 大小时间戳 / commit hash）、**没做成或不确定的具体项**，都要写清。
