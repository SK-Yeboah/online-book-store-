package com.bookstore.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class JwtResponse {

    private String accessToken;
    private String refreshToken;
    private String username;
    private String role;
    private String tokenType = "Bearer";

    public JwtResponse(String accessToken, String refreshToken, String username, String role){

        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.role = role;
    }

    
}
