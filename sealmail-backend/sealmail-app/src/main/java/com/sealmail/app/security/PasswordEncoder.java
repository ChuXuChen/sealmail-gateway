package com.sealmail.app.security;

/**
 * 密码编码器接口
 */
public interface PasswordEncoder {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
