# 匠魂新生 · Tinkers' Newlife

一个面向 **Minecraft 1.20.1 Forge** 的大型内容向整合包 mod，
在 [匠魂生活（Tinkers' Construct）] 的基础上扩展出自己的部件、工具、流体、强化与副本系统。

## 环境

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.26 |
| Tinkers' Construct | 3.12.1.231 |
| Mantle | 1.11.117 |
| Java | 17 |

## 构建

```bash
# ① JSON 校验（必须 bad 0）
powershell -ExecutionPolicy Bypass -File tools/check-json.ps1

# ② 构建（必须 BUILD SUCCESSFUL）
./gradlew build --console=plain

# ③ 打单一合并 jar（推荐）
./gradlew singleJar --console=plain
```

产物：`build/libs/tinkersnewlife-<version>.jar`

## 部署

- `tools/deploy.ps1` 会硬编码拷贝到**两个实例**（测试包 + NL 包），
  **检测到任何 Minecraft 在跑就全部拒绝** —— 盖运行中的 jar 会 `NoClassDefFoundError`。
- 两边务必校对 **MD5 一致**。

## 模块结构

| 目录 | 作用 |
| --- | --- |
| `common/` | 主体：物品、方块、材料、流体、强化、数据包、资源包、混入 |
| `imagination/` | “幻想系”内容：便携设备、工具包等 |
| `cursed/` | “诅术/头衔”系统 |
| `apostle/` | 使徒增强模块 |
| `art/` | 美术相关模块 |
| `tools/` | 开发脚本（校验、部署、生成等） |
| `docs/开发备忘录.md` | **开发纪要（按节号追加，旧节不改）** |

## 开发约定

详见 [`AGENTS.md`](AGENTS.md)。摘要：

- 只改 `mod_version`，勿动其它版本号；
- 每次改动走：`check-json` → `build` → 部署 → 追加备忘录一节 → `git` 提交推送；
- **绝不覆盖** `textures/**` 与 `models/**` 下的既有美术资源；
- **未经点名不加工具提示（tooltip）**；
- 中文 `.ps1` 必须 **UTF-8 带 BOM**。

## 协议

本项目采用 [MIT 许可证](LICENSE)。

> 注：`LICENSE.txt` 是 **Minecraft Forge 自身**的 LGPL 2.1 声明（随 MDK 带来），
> `LICENSES/` 下是引用的第三方许可证，两者与本 mod 的协议无关，不要删。
