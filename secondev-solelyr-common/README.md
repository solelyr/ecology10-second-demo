# secondev-solelyr-common

E10 二开公共模块，提供流程表单查询、ESB 动作入口和数据源操作工具。业务代码位于 `com.weaver.seconddev.solelyr.workflow`。

## 代码功能

| 入口 | 用途 |
| --- | --- |
| `WorkflowFormQueryService` | 按流程实例 ID 查询流程上下文、字段定义及已保存的主表和明细数据。 |
| `WorkflowEsbForm`、`WorkflowFieldInfo` | 封装查询结果和字段信息。`mainData` 按数据库列名取值，`detailData` 按明细表名分组，`mainText` 和 `detailText` 提供选项名称、人员姓名等显示内容。 |
| `WorkflowEsbAction`、`WorkflowEsbActionHandler<R>` | 提供 ESB 动作入口。可直接返回表单数据，也可按 `serviceName` 将结果交给业务处理器。 |
| `WorkflowEsbController`、`WorkflowEsbFormMapper<T>` | 提供表单查询和结果转换接口。Mapper 将通用表单结果转换成业务实体。 |
| `SqlExecutor`、`SecondevDataSourceGroup` | 通过 E10 数据源接口执行查询、更新和事务。数据源分组接口用于提供分组 ID 等配置。 |
| `ColumnNameResolver` | 将平台字段定义解析为数据库列名，供表单结果按列名取值。 |

### Java 调用

在 Spring Bean 中注入 `WorkflowFormQueryService`，传入流程实例 ID、操作用户 ID 和租户标识。主表按列名取值，明细按数据库表名取行。

```java
WorkflowEsbForm form = workflowFormQueryService.query(requestId, employeeId, tenantKey);
Object amount = form.getMainValue("amount");
List<Map<String, Object>> rows = form.getDetailRows("formtable_main_123_dt1");
```

将示例中的 `amount` 和 `formtable_main_123_dt1` 换成实际表单的数据库列名和表名。

### ESB 动作和 HTTP 入口

- ESB 动作使用 Spring Bean `solelyrWorkflowEsbAction`，示例入口为 `solelyrWorkflowEsbActionDemo`。传入 `{"requestId":"123456"}` 即可查询表单，`requestid` 也可作为参数名。以字符串传递长整型 ID 可避免调用端精度丢失。
- 需要在查询后处理业务逻辑时，实现 `WorkflowEsbActionHandler<R>` 并注册为 Spring Bean。将 Bean 名称作为 `serviceName` 传入，处理结果会放在响应的 `data` 中。处理器的返回值须能转换为 JSON 对象，也可以返回 `null`。
- Controller 声明的相对路由为 `GET /workflow/esb/form?requestid=123456` 和 `GET /workflow/esb/form/mapped?requestid=123456&mapper=myFormMapper`。第二个接口要求注册 `WorkflowEsbFormMapper<T>` Bean，并通过 `mapper` 指定名称。调用时按目标 E10 环境补齐网关或二开服务前缀。

## 文件清单插件

本模块已启用 `com.weaver.seconddev.solelyr-file-manifest`。插件由仓库的 [`buildSrc`](../buildSrc/src/main/java/com/weaver/ecode/gradle/plugin/SolelyrFileManifestPlugin.java) 提供，扫描模块 `main` 源集中的 Java 源码，生成供 E10 使用的 `file-manifest.json`。

### Gradle 配置

本模块已有以下配置。同一个 Gradle 根项目下的其他 Java 模块也可以使用这个插件 ID，并设置自己的模块描述。

```groovy
plugins {
    id 'java'
    id 'com.weaver.seconddev.solelyr-file-manifest'
}

description = 'solelyr 二开模块'
```

### 在独立 Gradle 项目中使用

`secondev-solelyr-common` JAR 是业务依赖。Gradle 在解析 `plugins {}` 时从当前构建的 `buildSrc` 加载插件，业务依赖中的 JAR 无法提供这个插件 ID。如果直接声明 ID 后出现 `Plugin ... was not found`，把以下文件复制到目标项目的根目录，保留 `buildSrc` 下的相对路径：

- [`buildSrc/build.gradle`](../buildSrc/build.gradle)
- [`buildSrc/src/main/java/com/weaver/ecode/gradle/plugin/SolelyrFileManifestPlugin.java`](../buildSrc/src/main/java/com/weaver/ecode/gradle/plugin/SolelyrFileManifestPlugin.java)
- [`buildSrc/src/main/resources/META-INF/gradle-plugins/com.weaver.seconddev.solelyr-file-manifest.properties`](../buildSrc/src/main/resources/META-INF/gradle-plugins/com.weaver.seconddev.solelyr-file-manifest.properties)

目标项目已有 `buildSrc/build.gradle` 时，将构建脚本中的 `gradleApi()`、`localGroovy()` 和 UTF-8 编译配置合入现有脚本。然后在需要生成清单的模块中按上面的示例声明插件 ID，无需指定版本。单模块项目运行 `./gradlew.bat generateFileManifest`，多模块项目运行 `./gradlew.bat :模块名:generateFileManifest`。

插件扫描目标模块自己的 `main` Java 源码。若目标项目还将 common JAR 的类合入部署 JAR，common 的清单条目需要另行合并。

### Javadoc 写法

将 Javadoc 放在与文件同名的顶层类型及其注解之前，日期填写实际创建时间。

```java
/**
 * @USER: solelyr
 * @DATE: 2026/11/14 23:45
 * @DESCRIPTION: 提供工作流表单查询接口并支持按 Mapper 转换返回实体
 */
```

- `@USER:` 写入清单条目的 `developer`，`@DESCRIPTION:` 写入 `desc`，多行描述会合并为一行。
- `@DATE:` 用于记录源码创建时间。清单条目还包含源码路径 `file`、类文件路径 `jarEntry`、`type` 和 `source`。
- 缺少 `@USER:` 或 `@DESCRIPTION:` 时，任务会列出对应文件并失败。顶层 `name` 和 `description` 取自当前 Gradle 模块。

### 生成与检查

使用 JDK 8，在仓库根目录执行：

```powershell
# 单独生成 JSON
.\gradlew.bat :secondev-solelyr-common:generateFileManifest

# 打包 JAR，自动生成并嵌入清单
.\gradlew.bat :secondev-solelyr-common:jar -PnexusAutoPublish=false
```

以下路径相对于本模块目录：

| 产物 | 路径 |
| --- | --- |
| 独立 JSON | `build/libs/<JAR 文件名去掉 .jar>-file-manifest.json` |
| 打包用 JSON | `build/generated/fileManifest/META-INF/file-manifest.json` |
| JAR 内路径 | `META-INF/file-manifest.json` |

如需运行公共模块的离线测试，先按[根 README](../README.md)准备 `secDevClasses` 和 `secDevLib`，再从仓库根目录运行 `./gradlew.bat :secondev-solelyr-common:solelyrWorkflowFormTest`。
