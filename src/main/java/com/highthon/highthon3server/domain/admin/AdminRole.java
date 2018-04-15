package com.highthon.highthon3server.domain.admin;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class AdminRole implements GrantedAuthority {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adminRoleId;

    @Column(nullable = false)
    private String email;

    @Enumerated(value = EnumType.STRING)
    private Role roleName;

    @Override
    public String getAuthority() {
        return roleName.getAuthority();
    }
}