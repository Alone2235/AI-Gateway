# AI Gateway

安卓端多模型聚合网关。一个 App，接所有免费大模型接口，429/503 自动切换。

## 解决的问题

免费 API 动不动 429、503，手动换太烦。这个 App 把多家 provider 聚合在一起：

- 多个 provider 统一管理（OpenRouter / Groq / Cerebras / Mistral / Gemini / DeepSeek / Moonshot / 硅基流动 / ModelScope / 智谱 / 自定义）
- 同一个 provider 可以填多个 API Key，自动轮换
- 429 / 503 / 5xx 自动降级到下一个可用接口，并让失败的接口进入冷却期
- 每个模型可单独设置降级策略

## 降级策略

| 策略 | 说明 |
|---|---|
| 严格 | 只用选中模型，失败就失败 |
| 换 Key | 同 provider 内轮换 API Key（默认） |
| 相近模型 | 降级到同 provider 的相近模型 |
| 完全自由 | 任何可用 provider/模型 |

## 冷却机制

| 错误 | 冷却时长 |
|---|---|
| 429 限流 | 60 秒 |
| 402 余额不足 | 30 分钟 |
| 401/403 鉴权 | 60 分钟 |
| 5xx 服务端 | 30 秒 |

## 构建

推送到 main 分支自动构建，APK 在 Actions 的 Artifacts 里下载。

本地构建：
```bash
./gradlew assembleRelease
```

签名：`keystore/eta-release.jks`，密码 `eta123456`。

## 技术栈

Kotlin · Jetpack Compose · Room · OkHttp SSE · DataStore · MVVM
