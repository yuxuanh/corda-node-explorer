package net.corda.explorer.controller;

import net.corda.explorer.exception.GenericException;
import net.corda.explorer.model.request.LoginRequest;
import net.corda.explorer.model.response.MessageResponseEntity;
import net.corda.explorer.model.response.Profile;
import net.corda.explorer.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
public class LoginController {

    @Autowired
    private LoginService loginService;

    @GetMapping("/server_awake")
    public MessageResponseEntity<String> server_awake() {
        return new MessageResponseEntity<>();
    }

    @PostMapping("/login")
    public MessageResponseEntity<Profile> login(@RequestBody LoginRequest loginRequest) {
        try {
            return new MessageResponseEntity<>(loginService.loginToNode(loginRequest));
        } catch (Exception e) {
            throw new GenericException(e.getMessage());
        }
    }

    @GetMapping("/profile")
    public MessageResponseEntity<Profile> getProfile() {
        return new MessageResponseEntity<>(loginService.getProfile());
    }
}