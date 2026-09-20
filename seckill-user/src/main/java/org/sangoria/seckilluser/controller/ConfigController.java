package org.sangoria.seckilluser.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RefreshScope
public class ConfigController {

    @Value("${seckill.title:默认标题}")
    private String title;

    @GetMapping("/config/title")
    public String getTitle() {
        return title;
    }
}
