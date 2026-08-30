package com.riskplatform.rating.adapter;

import com.riskplatform.common.model.PagedResult;
import com.riskplatform.rating.application.MerchantRatingService;
import com.riskplatform.rating.domain.MerchantRatingListView;
import com.riskplatform.rating.domain.MerchantRatingQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户评级列表查询（R12.7）：默认按更新时间降序分页展示。
 */
@RestController
@RequestMapping("/api/v1/merchant-ratings")
public class MerchantRatingListController {

    private final MerchantRatingService ratingService;

    public MerchantRatingListController(MerchantRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public PagedResult<MerchantRatingListView> list(
            @RequestParam(name = "merchantId", required = false) String merchantId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "level", required = false) String level,
            @RequestParam(name = "startTimeMs", required = false) Long startTimeMs,
            @RequestParam(name = "endTimeMs", required = false) Long endTimeMs,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "pageSize", required = false, defaultValue = "20") int pageSize) {
        return ratingService.list(new MerchantRatingQuery(
                merchantId, status, level, startTimeMs, endTimeMs, page, pageSize));
    }
}
