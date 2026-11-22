package com.weaver.seconddev.solelyr.workflow.util;

import cn.hutool.core.codec.Base64;
import com.weaver.ebuilder.datasource.api.entity.ExecuteSqlEntity;
import com.weaver.ebuilder.datasource.api.entity.SqlParamEntity;
import com.weaver.ebuilder.datasource.api.enums.SourceType;
import com.weaver.ebuilder.datasource.api.enums.SqlParamType;
import com.weaver.ebuilder.datasource.api.service.DataSetService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * @USER: solelyr
 * @DATE: 2026/9/23 20:02
 * @DESCRIPTION: 通过 E10 数据源执行 SQL 查询、更新和事务操作
 * 基于 E10 {@link DataSetService} 的通用 SQL 执行器。
 *
 * <p>SQL 使用 {@code ?} 作为占位符，参数通过 {@link SqlParamEntity} 交给平台底层
 * {@code PreparedStatement} 绑定；调用方不得自行拼接参数值。</p>
 */
@Component
public class SqlExecutor {

    /** 默认数据源类型。 */
    public static final SourceType DEFAULT_SOURCE_TYPE = SourceType.LOGIC;
    /** 日期参数统一使用的业务时区。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** E10 平台数据源服务。 */
    private final DataSetService dataSetService;

    /**
     * 创建通用 SQL 执行器。
     *
     * @param dataSetService E10 平台数据源服务
     */
    public SqlExecutor(DataSetService dataSetService) {
        this.dataSetService = dataSetService;
    }

    /**
     * 使用枚举指定逻辑数据源并执行查询。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param sql 包含 {@code ?} 占位符的 SELECT SQL
     * @param parameters 按占位符顺序排列的参数
     * @return 查询结果行；无数据时返回空集合
     */
    public List<Map<String, Object>> executeQuery(SecondevDataSourceGroup dataSourceGroup,
                                                   String sql,
                                                   Object... parameters) {
        return executeQuery(DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), sql, parameters);
    }

    /**
     * 使用调用方传入的 groupId 执行逻辑数据源查询。
     *
     * @param groupId 数据源分组 ID
     * @param sql 包含 {@code ?} 占位符的 SELECT SQL
     * @param parameters 按占位符顺序排列的参数
     * @return 查询结果行；无数据时返回空集合
     */
    public List<Map<String, Object>> executeQuery(String groupId,
                                                   String sql,
                                                   Object... parameters) {
        return executeQuery(DEFAULT_SOURCE_TYPE, groupId, sql, parameters);
    }

    /**
     * 使用指定数据源类型和 groupId 执行查询。
     *
     * @param sourceType 数据源类型
     * @param groupId 数据源分组 ID
     * @param sql 包含 {@code ?} 占位符的 SELECT SQL
     * @param parameters 按占位符顺序排列的参数
     * @return 查询结果行；无数据时返回空集合
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> executeQuery(SourceType sourceType,
                                                   String groupId,
                                                   String sql,
                                                   Object... parameters) {
        validateSql(sourceType, groupId, sql, parameters, true);
        ExecuteSqlEntity entity = buildSqlEntity(sourceType, groupId, sql, parameters);
        Map<String, Object> response = requireSuccess(dataSetService.executeSql(entity));

        Object records = getIgnoreCase(response, "records");
        if (records == null) {
            return Collections.emptyList();
        }
        if (!(records instanceof List)) {
            throw new IllegalStateException("数据库查询结果 records 不是列表结构");
        }

        // DataSetService 约定 records 中每一项都是字段名到字段值的映射。
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object record : (List<?>) records) {
            if (!(record instanceof Map)) {
                throw new IllegalStateException("数据库查询结果包含非 Map 数据行");
            }
            rows.add((Map<String, Object>) record);
        }
        return rows;
    }

    /**
     * 使用枚举指定逻辑数据源并执行非事务更新语句。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param sql 包含 {@code ?} 占位符的 INSERT、UPDATE 或 DELETE SQL
     * @param parameters 按占位符顺序排列的参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeUpdate(SecondevDataSourceGroup dataSourceGroup,
                                              String sql,
                                              Object... parameters) {
        return executeUpdate(DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), sql, parameters);
    }

    /**
     * 使用调用方传入的 groupId 执行逻辑数据源非事务更新语句。
     *
     * @param groupId 数据源分组 ID
     * @param sql 包含 {@code ?} 占位符的 INSERT、UPDATE 或 DELETE SQL
     * @param parameters 按占位符顺序排列的参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeUpdate(String groupId,
                                              String sql,
                                              Object... parameters) {
        return executeUpdate(DEFAULT_SOURCE_TYPE, groupId, sql, parameters);
    }

    /**
     * 使用指定数据源类型和 groupId 执行非事务更新语句。
     *
     * @param sourceType 数据源类型
     * @param groupId 数据源分组 ID
     * @param sql 包含 {@code ?} 占位符的更新 SQL
     * @param parameters 按占位符顺序排列的参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeUpdate(SourceType sourceType,
                                              String groupId,
                                              String sql,
                                              Object... parameters) {
        validateSql(sourceType, groupId, sql, parameters, false);
        ExecuteSqlEntity entity = buildSqlEntity(sourceType, groupId, sql, parameters);
        return requireSuccess(dataSetService.executeSql(entity));
    }

    /**
     * 开启事务并执行第一条 SQL。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param transactionId 同一事务内保持一致的事务 ID
     * @param sql 第一条事务 SQL
     * @param parameters SQL 参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> startTransaction(SecondevDataSourceGroup dataSourceGroup,
                                                 String transactionId,
                                                 String sql,
                                                 Object... parameters) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), transactionId, sql, true, false, false, parameters);
    }

    /**
     * 使用调用方传入的 groupId 开启事务并执行第一条 SQL。
     *
     * @param groupId 数据源分组 ID
     * @param transactionId 同一事务内保持一致的事务 ID
     * @param sql 第一条事务 SQL
     * @param parameters SQL 参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> startTransaction(String groupId,
                                                 String transactionId,
                                                 String sql,
                                                 Object... parameters) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, groupId, transactionId, sql, true, false, false, parameters);
    }

    /**
     * 在已开启事务中执行后续 SQL。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param transactionId 已开启事务的 ID
     * @param sql 事务 SQL
     * @param parameters SQL 参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeInTransaction(SecondevDataSourceGroup dataSourceGroup,
                                                     String transactionId,
                                                     String sql,
                                                     Object... parameters) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), transactionId, sql, false, false, false, parameters);
    }

    /**
     * 使用调用方传入的 groupId 在已开启事务中执行后续 SQL。
     *
     * @param groupId 数据源分组 ID
     * @param transactionId 已开启事务的 ID
     * @param sql 事务 SQL
     * @param parameters SQL 参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeInTransaction(String groupId,
                                                     String transactionId,
                                                     String sql,
                                                     Object... parameters) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, groupId, transactionId, sql, false, false, false, parameters);
    }

    /**
     * 提交指定事务。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param transactionId 待提交事务的 ID
     * @return DataSetService 成功响应
     */
    public Map<String, Object> commitTransaction(SecondevDataSourceGroup dataSourceGroup,
                                                  String transactionId) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), transactionId, "", false, true, false);
    }

    /**
     * 使用调用方传入的 groupId 提交指定事务。
     *
     * @param groupId 数据源分组 ID
     * @param transactionId 待提交事务的 ID
     * @return DataSetService 成功响应
     */
    public Map<String, Object> commitTransaction(String groupId, String transactionId) {
        return executeTransaction(DEFAULT_SOURCE_TYPE, groupId, transactionId, "", false, true, false);
    }

    /**
     * 回滚指定事务。
     *
     * @param dataSourceGroup 数据源分组枚举
     * @param transactionId 待回滚事务的 ID
     * @return DataSetService 成功响应
     */
    public Map<String, Object> rollbackTransaction(SecondevDataSourceGroup dataSourceGroup,
                                                    String transactionId) {
        return executeTransaction(
                DEFAULT_SOURCE_TYPE, requireGroupId(dataSourceGroup), transactionId, "", false, false, true);
    }

    /**
     * 使用调用方传入的 groupId 回滚指定事务。
     *
     * @param groupId 数据源分组 ID
     * @param transactionId 待回滚事务的 ID
     * @return DataSetService 成功响应
     */
    public Map<String, Object> rollbackTransaction(String groupId, String transactionId) {
        return executeTransaction(DEFAULT_SOURCE_TYPE, groupId, transactionId, "", false, false, true);
    }

    /**
     * 执行带事务控制标志的 SQL。
     *
     * @param sourceType 数据源类型
     * @param groupId 数据源分组 ID
     * @param transactionId 事务 ID
     * @param sql SQL；单独提交或回滚时可为空字符串
     * @param startTransaction 是否开启事务
     * @param commitTransaction 是否提交事务
     * @param rollbackTransaction 是否回滚事务
     * @param parameters SQL 参数
     * @return DataSetService 成功响应
     */
    public Map<String, Object> executeTransaction(SourceType sourceType,
                                                   String groupId,
                                                   String transactionId,
                                                   String sql,
                                                   boolean startTransaction,
                                                   boolean commitTransaction,
                                                   boolean rollbackTransaction,
                                                   Object... parameters) {
        validateTransaction(sourceType, groupId, transactionId, sql,
                startTransaction, commitTransaction, rollbackTransaction, parameters);
        ExecuteSqlEntity entity = buildSqlEntity(sourceType, groupId, sql, parameters);
        entity.setTransactionId(transactionId);
        entity.setStartTransaction(startTransaction);
        entity.setCommitTransaction(commitTransaction);
        entity.setRollbackTransaction(rollbackTransaction);
        return requireSuccess(dataSetService.executeSqlAll(entity));
    }

    /**
     * 生成一个可用于多次事务调用的唯一事务 ID。
     *
     * @return UUID 格式事务 ID
     */
    public String newTransactionId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 构造 DataSetService 所需的 SQL 请求对象。
     */
    private ExecuteSqlEntity buildSqlEntity(SourceType sourceType,
                                             String groupId,
                                             String sql,
                                             Object[] parameters) {
        ExecuteSqlEntity entity = new ExecuteSqlEntity();
        entity.setSourceType(sourceType);
        entity.setGroupId(groupId);
        entity.setSql(Base64.encode(sql == null ? "" : sql));
        List<SqlParamEntity> sqlParameters = buildParameters(parameters);
        if (!sqlParameters.isEmpty()) {
            entity.setParams(sqlParameters);
        }
        return entity;
    }

    /**
     * 校验并返回枚举中的 groupId。
     */
    private String requireGroupId(SecondevDataSourceGroup dataSourceGroup) {
        if (dataSourceGroup == null) {
            throw new IllegalArgumentException("数据源分组枚举不能为空");
        }
        return dataSourceGroup.getGroupId();
    }

    /**
     * 校验 DataSetService 返回结果。
     *
     * @param response DataSetService 原始响应
     * @return 已通过成功状态校验的响应
     */
    private Map<String, Object> requireSuccess(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("DataSetService 未返回结果");
        }
        Object code = getIgnoreCase(response, "code");
        Object status = getIgnoreCase(response, "status");
        boolean codeSuccess = code == null || "200".equals(String.valueOf(code));
        boolean statusSuccess = status == null
                || "OK".equalsIgnoreCase(String.valueOf(status))
                || "SUCCESS".equalsIgnoreCase(String.valueOf(status))
                || String.valueOf(status).toLowerCase(Locale.ROOT).endsWith(" success");
        if (!codeSuccess || !statusSuccess) {
            Object message = getIgnoreCase(response, "message");
            throw new IllegalStateException("数据库执行失败：" + (message == null ? "未知错误" : message));
        }
        return response;
    }

    /**
     * 将调用参数转换为平台 SQL 参数对象。
     */
    private List<SqlParamEntity> buildParameters(Object[] parameters) {
        if (parameters == null || parameters.length == 0) {
            return Collections.emptyList();
        }
        List<SqlParamEntity> result = new ArrayList<>(parameters.length);
        for (Object parameter : parameters) {
            SqlParamEntity sqlParameter = new SqlParamEntity();
            sqlParameter.setParamType(resolveParamType(parameter));
            sqlParameter.setValue(formatParamValue(parameter));
            result.add(sqlParameter);
        }
        return result;
    }

    /**
     * 根据 Java 参数类型推断平台 SQL 参数类型。
     */
    private SqlParamType resolveParamType(Object parameter) {
        if (parameter == null) {
            return SqlParamType.NULL;
        }
        if (parameter instanceof Integer) {
            return SqlParamType.INTEGER;
        }
        if (parameter instanceof Short) {
            return SqlParamType.SMALLINT;
        }
        if (parameter instanceof Byte) {
            return SqlParamType.TINYINT;
        }
        if (parameter instanceof Long) {
            return SqlParamType.LONG;
        }
        if (parameter instanceof BigDecimal || parameter instanceof BigInteger) {
            return SqlParamType.BIGDECIMAL;
        }
        if (parameter instanceof Double) {
            return SqlParamType.DOUBLE;
        }
        if (parameter instanceof Float) {
            return SqlParamType.FLOAT;
        }
        if (parameter instanceof Boolean) {
            return SqlParamType.BOOLEAN;
        }
        if (parameter instanceof byte[]) {
            return SqlParamType.BYTE;
        }
        if (parameter instanceof java.sql.Date || parameter instanceof LocalDate) {
            return SqlParamType.DATE;
        }
        if (parameter instanceof Time) {
            return SqlParamType.TIME;
        }
        if (parameter instanceof Timestamp
                || parameter instanceof Date
                || parameter instanceof LocalDateTime
                || parameter instanceof Instant
                || parameter instanceof OffsetDateTime
                || parameter instanceof ZonedDateTime) {
            return SqlParamType.TIMESTAMP;
        }
        return SqlParamType.STRING;
    }

    /**
     * 将 Java 参数格式化为平台参数文本。
     */
    private String formatParamValue(Object parameter) {
        if (parameter == null) {
            return null;
        }
        if (parameter instanceof byte[]) {
            return Base64.encode((byte[]) parameter);
        }
        if (parameter instanceof LocalDate) {
            return String.valueOf(((LocalDate) parameter).atStartOfDay(BUSINESS_ZONE).toInstant().toEpochMilli());
        }
        if (parameter instanceof LocalDateTime) {
            return String.valueOf(((LocalDateTime) parameter).atZone(BUSINESS_ZONE).toInstant().toEpochMilli());
        }
        if (parameter instanceof Instant) {
            return String.valueOf(((Instant) parameter).toEpochMilli());
        }
        if (parameter instanceof OffsetDateTime) {
            return String.valueOf(((OffsetDateTime) parameter).toInstant().toEpochMilli());
        }
        if (parameter instanceof ZonedDateTime) {
            return String.valueOf(((ZonedDateTime) parameter).toInstant().toEpochMilli());
        }
        if (parameter instanceof Date) {
            return String.valueOf(((Date) parameter).getTime());
        }
        if (parameter instanceof Enum) {
            return ((Enum<?>) parameter).name();
        }
        return String.valueOf(parameter);
    }

    /**
     * 校验普通查询或更新 SQL。
     */
    private void validateSql(SourceType sourceType,
                             String groupId,
                             String sql,
                             Object[] parameters,
                             boolean queryOnly) {
        validateCommon(sourceType, groupId, sql, parameters, false);
        String normalizedSql = sql.trim().toLowerCase(Locale.ROOT);
        boolean query = normalizedSql.startsWith("select") || normalizedSql.startsWith("with");
        if (queryOnly && !query) {
            throw new IllegalArgumentException("executeQuery 仅允许执行 SELECT 查询");
        }
        if (!queryOnly && query) {
            throw new IllegalArgumentException("executeUpdate 不允许执行 SELECT 查询");
        }
    }

    /**
     * 校验事务参数和互斥的事务控制标志。
     */
    private void validateTransaction(SourceType sourceType,
                                     String groupId,
                                     String transactionId,
                                     String sql,
                                     boolean startTransaction,
                                     boolean commitTransaction,
                                     boolean rollbackTransaction,
                                     Object[] parameters) {
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new IllegalArgumentException("事务 ID 不能为空");
        }
        int controlCount = (startTransaction ? 1 : 0)
                + (commitTransaction ? 1 : 0)
                + (rollbackTransaction ? 1 : 0);
        if (controlCount > 1) {
            throw new IllegalArgumentException("开启、提交和回滚事务标志不能同时使用");
        }
        boolean finishOnly = commitTransaction || rollbackTransaction;
        validateCommon(sourceType, groupId, sql, parameters, finishOnly);
        if (!finishOnly && (sql == null || sql.trim().isEmpty())) {
            throw new IllegalArgumentException("事务执行 SQL 不能为空");
        }
    }

    /**
     * 校验数据源、SQL 和占位符数量。
     */
    private void validateCommon(SourceType sourceType,
                                String groupId,
                                String sql,
                                Object[] parameters,
                                boolean allowEmptySql) {
        if (sourceType == null) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }
        if (groupId == null || groupId.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源分组不能为空");
        }
        if (!allowEmptySql && (sql == null || sql.trim().isEmpty())) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String actualSql = sql == null ? "" : sql;
        int placeholderCount = countPlaceholders(actualSql);
        int parameterCount = parameters == null ? 0 : parameters.length;
        if (placeholderCount != parameterCount) {
            throw new IllegalArgumentException(
                    "SQL 占位符数量与参数数量不一致：占位符 " + placeholderCount + " 个，参数 " + parameterCount + " 个");
        }
    }

    /**
     * 统计不位于引号或注释内的 {@code ?} 占位符。
     */
    private int countPlaceholders(String sql) {
        int count = 0;
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < sql.length(); index++) {
            char current = sql.charAt(index);
            char next = index + 1 < sql.length() ? sql.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '-' && next == '-') {
                lineComment = true;
                index++;
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '/' && next == '*') {
                blockComment = true;
                index++;
                continue;
            }
            if (!doubleQuote && current == '\'' && next == '\'') {
                index++;
                continue;
            }
            if (!doubleQuote && current == '\'') {
                singleQuote = !singleQuote;
                continue;
            }
            if (!singleQuote && current == '"') {
                doubleQuote = !doubleQuote;
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '?') {
                count++;
            }
        }
        return count;
    }

    /** 忽略字段名大小写读取平台响应字段。 */
    private Object getIgnoreCase(Map<String, Object> values, String expectedKey) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(expectedKey)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
