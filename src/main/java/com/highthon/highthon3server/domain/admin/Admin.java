package com.highthon.highthon3server.domain.admin;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Admin implements UserDetails {

    @Id
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    private String adminId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String belong;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(nullable = false, length = 15, unique = true)
    private String phone;

    @CreationTimestamp
    private LocalDateTime createdDate;

    @UpdateTimestamp
    private LocalDateTime updatedDate;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "email", referencedColumnName = "email")
    private List<AdminRole> roles;

    @Column
    private LocalDateTime lastPasswordResetDate;

    @Builder
    public Admin(String name, String email, String belong, String password, String phone) {
        this.name = name;
        this.email = email;
        this.belong = belong;
        this.password = password;
        this.phone = phone;
    }

    public void setRoles(Role... roles) {
        this.roles = Arrays.stream(roles).map(role -> new AdminRole(email, role)).collect(Collectors.toList());
    }

    public void addRoles(Role... roles) {
        List<AdminRole> paramRoles = Arrays.stream(roles).map(role -> new AdminRole(email, role)).collect(Collectors.toList());
        if (this.roles == null) {
            this.roles = paramRoles;
        } else {
            this.roles.addAll(paramRoles);
        }
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
