# 色温检测 0.1.5

独立的原生 Android 应用，包名 `cn.colortemperature.meter`，最低 Android 11。安装包为 `ColorTemperature-0.1.5.apk`，对应源码包为 `ColorTemperature-0.1.5-source.zip`。

## 功能

- 测量页：相关色温 K、暖 / 中性 / 冷光分类、暖冷色带、环境照度、最近 60 个采样点的趋势。
- 暂停 / 继续：暂停时取消订阅，保留读数；继续时清除旧会话并重新接收数据。离开前台也取消订阅。
- 本机记录：保存按钮点击时锁定读数，填写备注后保存，最多 100 条。支持查看、复制、删除和通过系统分享导出文本。
## Color Temperature 0.1.5
A standalone native Android app with the package name cn.colortemperature.meter. Requires Android 11 or later. The installation package is ColorTemperature-0.1.5.apk, and the corresponding source archive is ColorTemperature-0.1.5-source.zip.
Features
- Measurement: correlated color temperature in kelvin (K), warm / neutral / cool light classification, a warm-to-cool color scale, illuminance, and a trend chart showing the latest 60 samples.
- Pause / Resume: pausing unsubscribes from sensor events and retains the current readings. Resuming clears the previous session and starts receiving new data. Subscriptions also stop when the app leaves the foreground.
- Local history: tapping Save captures the current reading. Add an optional note and save up to 100 records. Records can be viewed, copied, deleted, or exported as text through the system share sheet.
Sensor Implementation and Current Limitations
Color temperature is exposed through vendor-specific sensor interfaces. Availability to ordinary apps and the format of event channels depend on the device system.

This version uses SensorManager.getSensorList(Sensor.TYPE_ALL) to find interfaces whose names or stringType values contain CCT or color temperature, prioritizing rear-facing, non-wake-up sensors. Automatic mode subscribes to non-factory CCT sources. Factory interfaces and sources marked stream, strm, or raw are subscribed to only when manually selected; their data is not interpreted automatically. Subscription failures, missing events, and invalid data are shown as distinct states, with diagnostic information retained.
