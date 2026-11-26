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

# 仅打发行 JAR 时，可用兼容入口
./gradlew singleJar --console=plain
```

产物：`build/libs/tinkersnewlife-<version>.jar`

## 部署

- [部署脚本](<tools/deploy.ps1>)默认更新测试包，加 `-IncludeNL` 才包含 NL；跳过运行中的实例，复制后核对 MD5。

## 项目结构

整个仓库是一个 ForgeGradle 项目，只注册 `tinkersnewlife`。原四部分保留 Java 包名，不再分别编译或打包。

| 目录 | 作用 |
| --- | --- |
| `src/main/java/` | 全部 Java 源码与联动 |
| `src/main/resources/` | Forge 入口元数据、Mixin 配置、数据包和美术资源 |
| `src/generated/resources/` | `runData` 的资源输出目录 |
| `gradle/` | Forge 依赖配置与 Gradle Wrapper |
| `art/` | 美术资料 |
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
