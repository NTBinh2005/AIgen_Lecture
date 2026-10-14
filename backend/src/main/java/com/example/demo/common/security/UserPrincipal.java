package com.example.demo.common.security;

import com.example.demo.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wrapper quanh {@link User} entity để Spring Security sử dụng.
 * Chứa userId, email, role dùng để inject vào Controller qua @AuthenticationPrincipal.
 */
public class UserPrincipal implements UserDetails {

    private final Integer userId;
    private final long tokenVersion;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public UserPrincipal(User user) {
        this.userId = user.getUserId();
        this.tokenVersion = user.getTokenVersion();
        this.email = user.getEmail() != null ? user.getEmail() : user.getPhoneNumber();
        this.password = user.getPasswordHash();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        this.enabled = user.getStatus() == com.example.demo.entity.UserStatus.ACTIVE;
    }

    public long getTokenVersion() { return tokenVersion; }

    public Integer getUserId() {
        return userId;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override public boolean isAccountNonExpired()  { return true; }
    @Override public boolean isAccountNonLocked()   { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()            { return enabled; }
}
