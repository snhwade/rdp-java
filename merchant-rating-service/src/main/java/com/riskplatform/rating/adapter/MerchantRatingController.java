package com.riskplatform.rating.adapter;

import com.riskplatform.rating.application.MerchantRatingService;
import com.riskplatform.rating.domain.MerchantRating;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 商户评级 REST 适配器（R12）。
 *
 * <ul>
 *   <li>POST /api/v1/merchants/{id}/rating 触发评级计算（R12.1/R12.6）</li>
 *   <li>GET  /api/v1/merchants/{id}/rating 查看评级（R12.7/R12.8）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantRatingController {

    private final MerchantRatingService ratingService;

    public MerchantRatingController(MerchantRatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/{id}/rating")
    public RatingView compute(@PathVariable("id") String merchantId, @RequestBody RatingRequest req) {
        return RatingView.from(ratingService.computeAndSave(merchantId, req.factors()));
    }

    @GetMapping("/{id}/rating")
    public RatingView query(@PathVariable("id") String merchantId) {
        return RatingView.from(ratingService.query(merchantId));
    }

    public record RatingRequest(Map<String, Double> factors) {
    }

    public record RatingView(String merchantId, Integer score, String level, String status) {
        static RatingView from(MerchantRating r) {
            return new RatingView(
                    r.getMerchantId(),
                    r.getScore(),
                    r.getLevel() == null ? null : r.getLevel().name(),
                    r.getStatus().name());
        }
    }
}
