package com.bookstore.security;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Instant;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class Jwtutil {

    private static final int MIN_KEY_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token.expiration:900000}")
    private Long accessTokenExpiration;

    private SecretKey signingKey;


    @PostConstruct
    public void init(){
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret must be set via JWT_SECRET environment variable. " +
                "Generate one with: openssl rand -hex 32"
            );
        }

        byte[] keyBytes = decodeHexSecret(secret);

        if(keyBytes.length < MIN_KEY_BYTES){
            throw new IllegalStateException(
                "JWT secret must be at least 32 bytes (64 hex chars). Got "+keyBytes+" bytes."
            );
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }


    private byte[] decodeHexSecret(String secret) {

        if(secret == null || secret.isBlank()){
            throw new IllegalStateException("jwt.secret must be set and non-empty");
        }

        String trim_secret = secret.trim();
        if(trim_secret.length() >= 64 && trim_secret.matches("[0-9a-fA-F]+")){
        
            // byte[] bytes = new byte[trim_secret.length()/2];
            // for(int i = 0; i < bytes.length; i++){
            //     int index = i * 2;
            //     bytes[i] =(byte) Integer.parseInt(trim_secret.substring(index, index + 2), 16);
            // }
            return HexFormat.of().parseHex(trim_secret);
        }
        return trim_secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateAccessToken(UserDetails UserDetails){
        return Jwts.builder()
                    .setSubject(UserDetails.getUsername())
                    .setId(UUID.randomUUID().toString())
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis()+accessTokenExpiration))
                    .signWith(signingKey, SignatureAlgorithm.HS256)
                    .compact();
    }

    public String extractUsername(String token){
        return parseClaims(token).getSubject();
    }

    public String extractJti(String token){
        return parseClaims(token).getId();
    }

    public Instant extractExpiry(String token){
        return parseClaims(token).getExpiration().toInstant();
    }

    public boolean validateToken(String token, UserDetails userdetails){
        if(token == null || token.isBlank()){
            return false;
        }
        try{
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(userdetails.getUsername()) && !claims.getExpiration().before(new Date());
        }catch(JwtException | IllegalArgumentException e){
            return false;
        }
    }

    private Claims parseClaims(String token) {
        if(token == null || token.isBlank()){
            throw new IllegalArgumentException("Token must not be nill");
        }
        return Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
    
    }




    
}
