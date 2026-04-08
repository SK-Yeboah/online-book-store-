package com.bookstore.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.bookstore.entity.User;

@RestController
public class UserController {

    @PostMapping("/login")
    public String login(@RequestBody User user){
        return "Success";
    }
    
}
