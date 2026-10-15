package com.meiyetec.dto;

import lombok.Data;
import java.util.List;

@Data
public class LoginVO {
    private Long id;
    private String username;
    private String realName;
    private String token;
    private List<String> roles;
    private List<String> permissions;
    private List<MenuNodeDTO> menus;
}
