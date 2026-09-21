package org.sangoria.seckillorder.controller;

import org.sangoria.common.constant.AuthConstants;
import org.sangoria.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class PingController {
    @GetMapping("/ping")
    public R<String> ping(
            @RequestHeader(value = AuthConstants.HEADER_USER_ID, required = false) Long userId) {
        return R.ok("pong from order, userId=" + userId);
    }
}