package com.fancy.taxiagent.exception;

/**
 * 未授权异常（401）
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(401, message);
    }
}
