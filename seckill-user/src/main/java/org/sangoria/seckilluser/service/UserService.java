package org.sangoria.seckilluser.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.sangoria.common.constant.AuthConstants;
import org.sangoria.common.exception.BizException;
import org.sangoria.common.jwt.JwtUtil;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.seckilluser.dto.*;
import org.sangoria.seckilluser.entity.SysUser;
import org.sangoria.seckilluser.mapper.SysUserMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    private static final String REFRESH_KEY = "seckill:refresh:";

    public void register(RegisterRequest req) {
        Long cnt = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (cnt != null && cnt > 0) throw new BizException(ErrorCode.USER_ALREADY_EXISTS);
        SysUser u = new SysUser();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setEmail(req.getEmail());
        u.setStatus(1);
        userMapper.insert(u);
    }

    public AuthResponse login(LoginRequest req) {
        SysUser u = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, req.getUsername()));
        if (u == null || !passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            throw new BizException(ErrorCode.USER_PASSWORD_ERROR);
        }
        if (u.getStatus() == null || u.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        // 加密配置平滑过度
        if (passwordEncoder.upgradeEncoding(u.getPassword())) {
            u.setPassword(passwordEncoder.encode(req.getPassword()));
            userMapper.updateById(u);
        }
        String access = jwtUtil.createAccessToken(u.getId(), u.getUsername(), List.of("ROLE_USER"));
        String refresh = jwtUtil.createRefreshToken(u.getId());
        redis.opsForValue().set(REFRESH_KEY + u.getId(), refresh, Duration.ofHours(12));
        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refresh);
        resp.setExpiresIn(900L);
        return resp;
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtUtil.parse(refreshToken);
        } catch (JwtException e) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!AuthConstants.TOKEN_TYPE_REFRESH.equals(claims.get(AuthConstants.CLAIM_TYPE))) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        Long userId = Long.valueOf(claims.getSubject());
        String cached = redis.opsForValue().get(REFRESH_KEY + userId);
        if (cached == null || !cached.equals(refreshToken)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ErrorCode.USER_NOT_FOUND);

        String access = jwtUtil.createAccessToken(u.getId(), u.getUsername(), List.of("ROLE_USER"));
        AuthResponse resp = new AuthResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresIn(900L);
        return resp;
    }

    public void logout(Long userId) {
        redis.delete(REFRESH_KEY + userId);
    }

    public UserDTO me(Long userId) {
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new BizException(ErrorCode.USER_NOT_FOUND);
        UserDTO d = new UserDTO();
        d.setId(u.getId());
        d.setUsername(u.getUsername());
        d.setEmail(u.getEmail());
        return d;
    }
}