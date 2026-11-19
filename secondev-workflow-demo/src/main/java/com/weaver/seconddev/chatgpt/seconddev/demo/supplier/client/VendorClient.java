package com.weaver.seconddev.chatgpt.seconddev.demo.supplier.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.AddVendorRequest;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.request.TokenRequest;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.AddVendorResponse;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.TokenResponse;
import com.weaver.seconddev.chatgpt.seconddev.demo.supplier.model.response.VendorCreated;
import com.weaver.seconddev.chatgpt.seconddev.integration.exception.IntegrationException;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * @USER: solelyr
 * @DATE: 2026/11/06 09:53
 * @DESCRIPTION: 复用唯一 HTTP 入口调用 Token 和供应商新增接口，解析实体化响应
 */
@Component("solelyrVendorClient")
public class VendorClient {
    /** 第三方接口根地址，填写时以 / 结尾。 */
    private static final String BASE_URL = "";
    /** 第三方 Token 接口的应用 ID。 */
    private static final String APP_ID = "";
    /** 第三方 Token 接口的应用密钥。 */
    private static final String SECRET_KEY = "";
    /** HTTP 连接超时，单位毫秒。 */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** HTTP 读取超时，单位毫秒。 */
    private static final int READ_TIMEOUT_MS = 15000;

    private final String appid;
    private final String secretkey;
    private final int connectTimeout;
    private final int readTimeout;

    /**
     * 使用本类常量创建第三方供应商接口客户端。
     */
    public VendorClient() {
        this(APP_ID, SECRET_KEY, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
    }

    /**
     * 创建第三方供应商接口客户端。
     *
     * @param appid 第三方应用 ID
     * @param secretkey 第三方应用密钥
     * @param connectTimeout 连接超时毫秒数
     * @param readTimeout 读取超时毫秒数
     */
    public VendorClient(String appid, String secretkey, int connectTimeout, int readTimeout) {
        if (connectTimeout <= 0 || readTimeout <= 0) {
            throw new IntegrationException(IntegrationException.Kind.CONFIGURATION, "HTTP 超时必须为正数");
        }
        this.appid = appid;
        this.secretkey = secretkey;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 发送 Token 和供应商新增接口共用的 JSON POST 请求。
     *
     * @param path 相对于第三方根地址的接口路径
     * @param json JSON 请求内容
     * @return 第三方响应正文
     */
    protected String post(String path, String json) {
        if (BASE_URL.isEmpty()) {
            throw new IntegrationException(IntegrationException.Kind.CONFIGURATION, "请填写第三方接口根地址 BASE_URL");
        }
        try (HttpResponse response = HttpRequest.post(BASE_URL + path)
                .header("Content-Type", "application/json;charset=UTF-8")
                .setConnectionTimeout(connectTimeout).setReadTimeout(readTimeout)
                .setFollowRedirects(false).disableCookie().setChunkedStreamingMode(4096).body(json).execute()) {
            if (!response.isOk()) throw new IntegrationException(IntegrationException.Kind.TRANSPORT,
                    "第三方 HTTP 请求失败，状态码：" + response.getStatus());
            return response.body();
        } catch (IntegrationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IntegrationException(IntegrationException.Kind.TRANSPORT, "第三方 HTTP 请求未完成，请核对服务日志", e);
        }
    }

    /**
     * 调用第三方认证接口获取 Token。
     *
     * @return 第三方返回的非空 Token
     */
    public String fetchToken() {
        if (appid.trim().isEmpty() || secretkey.trim().isEmpty()) {
            throw new IntegrationException(IntegrationException.Kind.VALIDATION, "缺少第三方 appid 或 secretkey");
        }
        TokenRequest request = new TokenRequest();
        request.setAppid(appid);
        request.setSecretkey(secretkey);
        TokenResponse response = parse(post("Api/Base/GetToken", JSON.toJSONString(request)), TokenResponse.class);
        checkSuccess(response.getCode(), response.getSuccess());
        String token = response.getMessage();
        if (token == null || token.trim().isEmpty()) {
            throw new IntegrationException(IntegrationException.Kind.VALIDATION, "Token 响应缺少 message");
        }
        return token.trim();
    }

    /**
     * 发送供应商新增请求并读取第三方确认的结果。
     *
     * @param request 已填入 Token 的供应商新增请求
     * @return 包含第三方确认编码的供应商新增结果
     */
    public VendorCreated add(AddVendorRequest request) {
        AddVendorResponse response = parse(post("Api/Vendor/Add", JSON.toJSONString(request)), AddVendorResponse.class);
        checkSuccess(response.getCode(), response.getSuccess());
        if (response.getData() == null || response.getData().size() != 1 || !(response.getData().get(0) instanceof String))
            throw new IntegrationException(IntegrationException.Kind.PROTOCOL, "新增响应未返回唯一字符串供应商编码，请人工核对");
        String vendorCode = ((String) response.getData().get(0)).trim();
        if (vendorCode.isEmpty()) {
            throw new IntegrationException(IntegrationException.Kind.VALIDATION, "新增响应供应商编码为空");
        }
        VendorCreated created = new VendorCreated();
        created.setVendorCode(vendorCode);
        return created;
    }

    /**
     * 将第三方响应解析为指定类型的实体。
     *
     * @param <T> 响应实体类型
     * @param body 第三方响应正文
     * @param type 响应实体的类
     * @return 解析后的响应实体
     */
    private <T> T parse(String body, Class<T> type) {
        try {
            T response = JSON.parseObject(body, type);
            if (response == null) throw new IntegrationException(IntegrationException.Kind.PROTOCOL, "第三方响应为空");
            return response;
        } catch (JSONException e) {
            throw new IntegrationException(IntegrationException.Kind.PROTOCOL, "第三方响应不符合接口实体结构", e);
        }
    }

    /**
     * 核对第三方业务码及成功标识。
     *
     * @param code 第三方业务响应码
     * @param success 第三方业务成功标识
     */
    private void checkSuccess(BigDecimal code, String success) {
        if (code == null || code.compareTo(BigDecimal.ONE) != 0 || !"true".equals(success))
            throw new IntegrationException(IntegrationException.Kind.REJECTED, "第三方业务处理未成功，请核对业务数据");
    }
}
