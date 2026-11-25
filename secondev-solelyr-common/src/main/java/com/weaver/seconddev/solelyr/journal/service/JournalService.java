package com.weaver.seconddev.solelyr.journal.service;

import com.weaver.seconddev.solelyr.journal.exception.JournalException;
import com.weaver.seconddev.solelyr.journal.entity.JournalResult;
import com.weaver.seconddev.solelyr.journal.entity.JournalUnit;
import com.weaver.seconddev.solelyr.journal.exception.JournalErrors;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
import com.weaver.seconddev.solelyr.workflow.exception.WorkflowEsbException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @USER: solelyr
 * @DATE: 2026/11/23 09:22
 * @DESCRIPTION: 使用调用日志保留发送凭据，使用幂等表保存当前可复用结果
 */
public final class JournalService {
    private static final Logger LOG = LoggerFactory.getLogger(JournalService.class);

    /**
     * @USER: solelyr
     * @DATE: 2026/11/23 20:03
     * @DESCRIPTION: 定义推送逻辑使用的请求状态，按当前表单解析单选值
     */
    private enum RequestStatus {
        RUNNING, SUCCESS, FAILED, UNKNOWN;

        String valueIn(JournalDto form) {
            return form.option("qqzt", name());
        }
    }

    /** OA 日志起止时间的格式及北京时间时区。 */
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    /** 当前这笔推送的流程、目标系统和幂等键上下文。 */
    private final JournalUnit unit;
    /** 每次实际推送或本地回执复用时打开调用日志表。 */
    private final Supplier<JournalDto> openCallLogForm;
    /** 本次处理使用的调用日志表。 */
    private JournalDto callLogForm;
    /** 第三方接口幂等状态表的 E10 RPC 访问对象。 */
    private final Supplier<JournalDto> openIdempotencyForm;
    private JournalDto idempotencyForm;
    /** 本次尝试对应的调用日志 OA 数据 ID，用于完成或核对该记录。 */
    private String callLogId;
    /** 本次尝试的开始时间，以 Unix 毫秒计，用于计算耗时。 */
    private long startedAtMillis;

    public JournalService(JournalUnit unit, Supplier<JournalDto> openCallLogForm, JournalDto idempotencyForm) {
        this(unit, openCallLogForm, () -> idempotencyForm);
    }

    /** 按需分别打开两张 OA 表，使本地失败留痕独立于幂等表。 */
    public static JournalService forForms(JournalUnit unit, Supplier<JournalDto> callLog, Supplier<JournalDto> state) {
        return new JournalService(unit, callLog, state);
    }

    private JournalService(JournalUnit unit, Supplier<JournalDto> openCallLogForm, Supplier<JournalDto> openIdempotencyForm) {
        this.unit = unit;
        this.openCallLogForm = openCallLogForm;
        this.openIdempotencyForm = openIdempotencyForm;
    }

    public String beginAttempt(String actionId) {
        startedAtMillis = System.currentTimeMillis();
        String storedData;
        try {
            storedData = readStoredData();
        } catch (RuntimeException failure) {
            recordUnsentFailure(actionId, "IDEMPOTENCY_LOOKUP", startedAtMillis, failure);
            throw failure;
        }
        Map<String, String> row = attemptRow(actionId, storedData == null ? "HTTP" : "LOCAL", RequestStatus.RUNNING);
        callLogId = callLogForm.write(unit, null, row);
        if (storedData != null) {
            finish(RequestStatus.SUCCESS, storedData, null, null, null, null);
        }
        return storedData;
    }

    private String readStoredData() {
        idempotencyForm = openIdempotencyForm.get();
        List<Map<String, String>> idempotencyRecords = idempotencyForm.find(unit, unit.key, "cgdjg");
        if (idempotencyRecords.size() > 1) {
            throw verify("幂等表中存在重复记录");
        }
        String storedData = null;
        if (!idempotencyRecords.isEmpty()) {
            storedData = idempotencyRecords.get(0).get("cgdjg");
            if (storedData == null) {
                throw verify("幂等记录缺少有效的成功回执");
            }
        }
        return storedData;
    }

    /** 发送前的本地失败独立新增一条终态日志，不修改其他尝试的记录。 */
    public void recordUnsentFailure(String actionId, String stage, long startedAt, RuntimeException failure) {
        String code = failure instanceof WorkflowEsbException
                ? ((WorkflowEsbException) failure).getErrorCode() : JournalErrors.STORAGE.code();
        LOG.error("Workflow push failed before dispatch: stage={}, requestId={}, actionId={}, operation={}, key={}, errorCode={}",
                stage, unit.requestId, actionId, unit.operation, unit.key, code);
        try {
            startedAtMillis = startedAt;
            Map<String, String> row = attemptRow(actionId, "LOCAL", RequestStatus.FAILED);
            putEndTime(row, Instant.now());
            String reason = failure instanceof WorkflowEsbException ? failure.getMessage() : "读取 OA 幂等信息失败";
            row.put("sbyyhyczy", stage + " [" + code + "] " + reason);
            callLogId = callLogForm.write(unit, null, row);
        } catch (RuntimeException writeFailure) {
            if (writeFailure != failure) {
                failure.addSuppressed(writeFailure);
            }
        }
    }

    private Map<String, String> attemptRow(String actionId, String source, RequestStatus status) {
        callLogForm = openCallLogForm.get();
        Map<String, String> row = identity(callLogForm);
        row.put("qqzt", status.valueIn(callLogForm));
        row.put("jgly", callLogForm.option("jgly", source));
        row.put("httff", unit.httpMethod);
        row.put("jkdz", unit.requestUrl);
        row.put("qqkssj", LOG_TIME_FORMAT.format(Instant.ofEpochMilli(startedAtMillis)));
        row.put("qqzw", unit.requestSummary);
        JSONObject context = new JSONObject();
        context.put("actionId", actionId);
        context.put("requestId", Long.toString(unit.requestId));
        context.put("object", unit.objectId);
        context.put("start", startedAtMillis);
        row.put("jctydxx", context.toJSONString());
        return row;
    }

    public void recordSuccess(JournalResult<?> result) {
        String storedData;
        try {
            storedData = result.data instanceof String ? (String) result.data : JSON.toJSONString(result.data);
        } catch (JSONException invalid) {
            throw verify("无法保存接口返回对象，请核对成功结果");
        }
        saveSnapshot(storedData);
        finish(RequestStatus.SUCCESS, storedData, result.httpStatus, result.businessCode, null, null);
    }

    public void recordFailure(JournalException failure) {
        finish(failure.uncertain ? RequestStatus.UNKNOWN : RequestStatus.FAILED, null, failure.httpStatus, failure.businessCode,
                failure.errorType, failure.getMessage());
    }

    public String getCallLogId() {
        return callLogId;
    }

    private void saveSnapshot(String storedData) {
        Map<String, String> values = identity(idempotencyForm);
        values.put("cgdjg", storedData);
        values.put("qqzw", unit.requestSummary);
        idempotencyForm.write(unit, null, values);
    }

    private void finish(RequestStatus status, String data, Integer httpStatus, String businessCode, String errorType, String message) {
        Instant now = Instant.now();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("qqzt", status.valueIn(callLogForm));
        putEndTime(fields, now);
        fields.put("xyzw", data);
        fields.put("httztm", httpStatus == null ? null : httpStatus.toString());
        fields.put("dsfywztm", businessCode);
        fields.put("cwlx", errorType == null ? null : callLogForm.option("cwlx", errorType));
        fields.put("sbyyhyczy", message);
        callLogForm.write(unit, callLogId, fields);
    }

    private void putEndTime(Map<String, String> fields, Instant now) {
        fields.put("qqjssj", LOG_TIME_FORMAT.format(now));
        fields.put("qqhshm", Long.toString(Math.max(0, now.toEpochMilli() - startedAtMillis)));
    }

    private Map<String, String> identity(JournalDto form) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("mbxt", form.option("mbxt", unit.system));
        values.put("mdj", unit.key);
        values.put("jkcz", unit.operation);
        return values;
    }

    private WorkflowEsbException verify(String reason) {
        return new WorkflowEsbException(JournalErrors.VERIFY, reason);
    }
}
