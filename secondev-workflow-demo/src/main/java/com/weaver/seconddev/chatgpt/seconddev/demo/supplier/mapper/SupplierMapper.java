package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.mapper;

import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.VendorArchive;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.action.SupplierSubmission;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.AddVendorRequest;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 将已确认的八项真实表单字段显式转换为第三方供应商实体
 */
public final class SupplierMapper {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    /**
     * 按正式动作契约映射新增请求。缺少已确认的发展日期时中断映射。
     *
     * @param input E10 流程传入的表单数据及动作参数
     * @return 通过必填校验的第三方供应商新增请求
     */
    public AddVendorRequest map(SupplierSubmission input) {
        if (input == null) throw invalid("缺少动作输入");
        // 当前没有已确认的发展日期来源，不读取申请日期或外部同名字段。
        return map(input, (String) null);
    }

    /**
     * 按真实 E10 字段映射新增请求，并使用调用方明确提供的发展日期。
     *
     * @param input E10 流程传入的表单数据及动作参数
     * @param developmentDate 格式为 yyyy-MM-dd 的发展日期
     * @return 通过必填校验的第三方供应商新增请求
     */
    public AddVendorRequest map(SupplierSubmission input, String developmentDate) {
        if (input == null || input.getMainData() == null) throw invalid("缺少动作输入或 mainData");
        Map<String, Object> mainData = input.getMainData();
        numeric(input.getFormId(), "formId");
        numeric(input.getFormDataId(), "formDataId"); numeric(input.getAppId(), "appId");
        AddVendorRequest request = new AddVendorRequest();
        request.setBillId(required(input.getBillId(), "缺少第三方 billid"));
        request.setAccId(required(input.getAccId(), "缺少第三方 AccId"));
        request.setLoginDate(input.getLoginDate() == null ? "" : input.getLoginDate().trim());
        if (!request.getLoginDate().isEmpty()) date(request.getLoginDate(), "LoginDate");
        VendorArchive a = new VendorArchive();
        a.setVendorCode(text(mainData, "gysbm", "供应商编码"));
        a.setVendorName(text(mainData, "gysmc", "供应商名称"));
        a.setVendorAbbreviation(text(mainData, "gysjc", "供应商简称"));
        a.setCategoryCode(text(mainData, "ssfl", "所属分类"));
        a.setCurrencyName(text(mainData, "bz", "币种"));
        a.setTaxNumber(text(mainData, "sh", "税号"));
        a.setBankName(text(mainData, "ssyx", "所属银行"));
        a.setBankAccount(text(mainData, "yxzh", "银行账号"));
        a.setDevelopmentDate(required(developmentDate,
                "发展日期 dVenDevDate 按用户要求留空，但第三方文档标为必填；当前不允许推送"));
        date(a.getDevelopmentDate(), "dVenDevDate");
        request.setArchives(a);
        return request;
    }

    /**
     * 按已核验的 dataKey 读取必填文本字段。
     *
     * @param values E10 主表数据
     * @param key 字段的 dataKey
     * @param label 字段名称，用于校验提示
     * @return 去除首尾空白后的字段值
     */
    private String text(Map<String, Object> values, String key, String label) {
        Object value = values.get(key);
        if (!(value instanceof String)) throw invalid(label + "（" + key + "）必须为字符串");
        return required((String) value, "缺少" + label + "（" + key + "）");
    }

    /**
     * 检查必填字符串并去除首尾空白。
     *
     * @param value 待校验的字符串
     * @param message 缺少有效值时的错误提示
     * @return 去除首尾空白后的非空字符串
     */
    private String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(message);
        }
        return value.trim();
    }

    /**
     * 检查日期是否符合 yyyy-MM-dd 格式且为有效日期。
     *
     * @param value 待校验的日期文本
     * @param target 第三方日期字段名称
     */
    private void date(String value, String target) {
        try {
            LocalDate.parse(value, DATE);
        } catch (Exception e) {
            throw invalid(target + " 必须是有效的 yyyy-MM-dd 日期");
        }
    }

    /**
     * 检查 E10 标识是否为 1 至 30 位数字字符串。
     *
     * @param value 待校验的标识
     * @param name 标识字段名称
     */
    private void numeric(String value, String name) {
        if (value == null || !value.matches("[0-9]{1,30}")) throw invalid(name + " 必须是实际映射的数字标识");
    }

    /**
     * 构造字段校验异常。
     *
     * @param message 字段校验失败说明
     * @return 校验类型的集成异常
     */
    private IntegrationException invalid(String message) {
        return new IntegrationException(IntegrationException.Kind.VALIDATION, message);
    }
}
