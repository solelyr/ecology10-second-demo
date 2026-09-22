# E10 二开项目

本项目使用 Gradle 6.9.4 管理多个 E10 二开 JAR 模块。所有二开代码必须位于 `com.weaver.seconddev` 包下。

## 模块

- `secondev-solelyr-semi`：solelyr 二开模块。
- `secondev-workflow-demo`：流程 Action 与接口示例模块。

新增模块时，目录名与模块内的 `.gradle` 文件名必须一致，并统一使用 `secondev-` 前缀。

## 本地依赖

- `secDevLib`：从二开服务 `weaver-secondev-service/webapps/ROOT/WEB-INF/lib` 复制的依赖 JAR。
- `secDevClasses`：从二开服务 `weaver-secondev-service/webapps/ROOT/WEB-INF/classes` 复制的编译依赖。
- `devLib`：项目额外依赖。

这些目录只用于本地编译和调试，不得提交到代码仓库，也不会打入二开 JAR。

## 构建

构建指定模块：

```powershell
.\gradlew.bat :secondev-solelyr-semi:clean :secondev-solelyr-semi:build
```

构建产物位于模块的 `build/libs`：

- `build.zip`
- `<模块名>.jar`

默认构建不生成 WAR。部署时上传 `build.zip` 或 JAR 到 `/ecode/monitor/loom/deploy/jar`。

## 本地 Debug 启动

`secondev-solelyr-semi` 已启用仅供本地调试的 War 模型，但默认 `war` 任务被禁用，因此点击 Gradle `build` 仍只生成 JAR 和 `build.zip`。

### IDEA Tomcat Local

1. 使用 JDK 8，并在 IDEA 的 Application Servers 中登记 `E:\tomcat-9.0.118`。
2. 重新加载 Gradle 项目；运行 `:secondev-solelyr-semi:prepareDebugWar` 可先验证本地运行包。
3. 选择项目提供的 `Tomcat 9.0.118` 运行配置。该配置使用 `8030` 端口，将 `secondev-solelyr-semi:war exploded` 部署到 `/`，并已包含本地私有化单体环境所需的 VM 参数。
4. 点击 IDEA 的 Debug；成功后访问 `http://localhost:8030/`。

注意：项目路径不能包含空格。2025.03 及之后的基线通常不能通过本地登录页登录，调用接口时需在请求中携带从目标 E10 环境取得的 Cookie。

## 资源和 Dubbo 配置

线上二开 JAR 必须排除 `src/main/resources`，避免把标准服务配置打入部署包。

`applicationContext-secondev-secondev-dubbo.xml` 中的二开 Dubbo 配置应在 e-code 监控管理平台的“配置文件编辑”入口维护，不应随 JAR 重复部署。Action 的 `ref` 和 `group` 必须唯一。

## 接口规范

- Web 接口：`/(s)api/secondev/...`
- 移动端接口：`/(s)api/app/secondev/...`
- 后台接口：`/(s)api/bs/secondev/...`
- Controller 返回值统一使用 `WeaResult`。
- 不得随意声明 `/papi`；对外接口使用 `/sapi` 并通过开放平台认证。
- 自定义 SQL 必须通过数据源接口执行，不使用 MyBatis。

参考：[E10 平台定制开发指引](https://www.e-cology.com.cn/ecode/playground/doc/share/view/916733818655342593#%E4%B8%89%E3%80%81%E5%90%8E%E7%AB%AF%E5%BC%80%E5%8F%91)
