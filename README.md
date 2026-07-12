# E33EPUS's ChatScreen - Fabric

将原版 Minecraft 聊天框替换为 QQ/微信风格的聊天气泡界面。

原项目: [E33EPUS/E33EPUS-s-ChatScreen](https://github.com/E33EPUS/E33EPUS-s-ChatScreen) (Forge)

## 功能特性

- **聊天气泡** - HUD 上显示 QQ/微信风格的聊天气泡，带头像、名称和渐变消失动画
- **点击交互** - 点击玩家头像 @提及，右键气泡弹出菜单（复制/引用回复）
- **引用回复** - 引用他人消息并高亮显示被引用的旧消息
- **消息管理** - 支持图片预览、@提醒强提示（放大加粗+音效）、未读红点
- **防刷屏** - 合并相同短消息为 `xN`
- **配置屏幕** - 按 O 键打开配置，支持多语言切换
- **聊天举报兼容** - 可选延迟显示模式

## 环境要求

- Minecraft 1.21.1
- Fabric Loader >= 0.16.0
- Fabric API
- Java >= 21

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/installer/)
2. 下载 [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods` 文件夹
3. 下载本模组 JAR 文件放入 `mods` 文件夹

## 操作说明

| 操作 | 功能 |
|------|------|
| `T` / `Enter` | 打开聊天气泡输入框 |
| `O` | 打开配置屏幕 |
| 点击玩家头像 | @提及该玩家 |
| 右键气泡 | 弹出菜单（复制/引用回复） |
| 未读时 `T` | 自动显示最新消息 |

## 构建

```bash
# 需要 Java 21
./gradlew build
```

生成的 JAR 文件在 `build/libs/` 目录下。

## 许可证

MIT License - 原作者 E33EPUS

## 致谢

- [E33EPUS](https://github.com/E33EPUS) - 原始 Forge 版本作者
- [Fabric](https://fabricmc.net/) - 模组加载器
- [Fabric API](https://github.com/FabricMC/fabric) - API 支持
