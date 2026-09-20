package org.sangoria.seckilluser.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.sangoria.common.constant.AuthConstants;
import org.sangoria.common.result.R;
import org.sangoria.seckilluser.dto.*;
import org.sangoria.seckilluser.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public R<Void> register(@RequestBody @Valid RegisterRequest req) {
        userService.register(req);
        return R.ok();
    }

    @PostMapping("/login")
    public R<AuthResponse> login(@RequestBody @Valid LoginRequest req) {
        return R.ok(userService.login(req));
    }

    @PostMapping("/refresh")
    public R<AuthResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return R.ok(userService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        userService.logout(userId);
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserDTO> me(@RequestHeader(AuthConstants.HEADER_USER_ID) Long userId) {
        return R.ok(userService.me(userId));
    }
}