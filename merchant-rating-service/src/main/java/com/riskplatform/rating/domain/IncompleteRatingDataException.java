package com.riskplatform.rating.domain;

import com.riskplatform.common.error.BizException;
import com.riskplatform.common.error.CommonErrorCode;

/**
 * 评级数据不完整异常（R12.4）：评级因子/指标缺失或不可读取时抛出，保留已有评级不变。
 */
public class IncompleteRatingDataException extends BizException {

    public IncompleteRatingDataException(String message) {
        super(CommonErrorCode.INVALID_STATE, message);
    }
}
