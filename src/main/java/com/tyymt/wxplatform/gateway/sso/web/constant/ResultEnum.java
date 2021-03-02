package com.tyymt.wxplatform.gateway.sso.web.constant;

/**
 * @author zou
 * @date 2019/4/9
 */
public enum ResultEnum {
    SUCCESS(10000, "成功"),

    UNKONW_ERROR(10001, "系统内部异常"),
    PARAMS_MISSING_ERROR(10002, "缺少必要参数"),
    PARAMS_ERROR(10003, "参数错误"),
    ERROR_POST_METHOD(10004, "访问方法异常"),
    TOKEN_EXPIRE(10005, "用户未登录"),
    TOKEN_ERROR(10006, "用户未登录"),
    TOKEN_REFRESH_ERROR(10007, "token刷新失败"),
    ILLEGAL_DATA_ERROR(10009, "非法请求"),
    REPEAT_REQUEST(10020,"请勿重复请求"),
    GATEWAY_ERROR(99999,"网关异常");

    private int code;
    private String message;

    ResultEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return this.code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
