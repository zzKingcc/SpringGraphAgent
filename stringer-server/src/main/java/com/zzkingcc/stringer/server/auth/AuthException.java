package com.zzkingcc.stringer.server.auth;

import com.zzkingcc.stringer.api.code.ErrorCode;
import com.zzkingcc.stringer.common.exception.BaseException;

/**
 * 账号 / 凭证类异常。
 * @author zzkingcc
 */
public class AuthException extends BaseException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
