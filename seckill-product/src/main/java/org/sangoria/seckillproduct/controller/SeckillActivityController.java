package org.sangoria.seckillproduct.controller;

import org.sangoria.common.result.R;
import org.sangoria.seckillproduct.entity.SeckillActivity;
import org.sangoria.seckillproduct.service.SeckillActivityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/product/seckill")
public class SeckillActivityController {

    private final SeckillActivityService activityService;

    public SeckillActivityController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/list")
    public R<List<SeckillActivity>> list() {
        return R.ok(activityService.listActive());
    }

    @GetMapping("/{activityId}")
    public R<SeckillActivity> detail(@PathVariable Long activityId) {
        return R.ok(activityService.requireById(activityId));
    }
}