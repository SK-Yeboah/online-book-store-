package com.bookstore.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {


    // @NotBlank
    // private String fname;
    
    // @NotBlank
    // private String lname;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String username;
    
    @NotBlank
    @Column(nullable = false)
    private String password;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.ROLE_USER;

    public User(String username, String password, String email, Role role){
        // this.fname = fname;
        // this.lname = lname;
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role;
    }


    public enum Role {
        ROLE_USER,
        ROLE_ADMIN
    }



}
