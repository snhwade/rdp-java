package com.riskplatform.ruleconfig.adapter.indicator;

import com.riskplatform.ruleconfig.application.indicator.IndicatorGroupAppService;
import com.riskplatform.ruleconfig.domain.indicator.IndicatorGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/indicator-groups")
public class IndicatorGroupController {

    private final IndicatorGroupAppService appService;

    public IndicatorGroupController(IndicatorGroupAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    public List<IndicatorGroupCardView> list() {
        List<IndicatorGroupCardView> cards = new ArrayList<>(
                appService.listCards().stream().map(IndicatorGroupCardView::from).toList());
        IndicatorGroupAppService.IndicatorGroupCard ungrouped = appService.ungroupedCard();
        if (ungrouped != null) {
            cards.add(IndicatorGroupCardView.from(ungrouped));
        }
        return cards;
    }

    @GetMapping("/{id}")
    public IndicatorGroupView get(@PathVariable("id") Long id) {
        return IndicatorGroupView.from(appService.get(id));
    }

    @PostMapping
    public IndicatorGroupView create(@Valid @RequestBody SaveIndicatorGroupRequest req) {
        return IndicatorGroupView.from(
                appService.create(req.name(), req.orgName(), req.eventTypeCodes(), req.description()));
    }

    @PutMapping("/{id}")
    public IndicatorGroupView update(@PathVariable("id") Long id,
                                     @Valid @RequestBody SaveIndicatorGroupRequest req) {
        return IndicatorGroupView.from(
                appService.update(id, req.name(), req.orgName(), req.eventTypeCodes(), req.description()));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        appService.delete(id);
    }

    public record SaveIndicatorGroupRequest(
            @NotBlank String name,
            String orgName,
            @NotEmpty List<String> eventTypeCodes,
            String description) {
    }

    public record IndicatorGroupView(
            Long id,
            String name,
            String orgName,
            List<String> eventTypeCodes,
            String description) {

        static IndicatorGroupView from(IndicatorGroup g) {
            return new IndicatorGroupView(
                    g.getId(), g.getName(), g.getOrgName(), g.getEventTypeCodes(), g.getDescription());
        }
    }

    public record IndicatorGroupCardView(
            Long id,
            String name,
            String orgName,
            List<String> eventTypeCodes,
            long onlineCount,
            long offlineCount) {

        static IndicatorGroupCardView from(IndicatorGroupAppService.IndicatorGroupCard c) {
            return new IndicatorGroupCardView(
                    c.id(), c.name(), c.orgName(), c.eventTypeCodes(), c.onlineCount(), c.offlineCount());
        }
    }
}
