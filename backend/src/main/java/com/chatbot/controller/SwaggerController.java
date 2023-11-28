package com.chatbot.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class SwaggerController {
  @GetMapping("/")
  public String index() {
    return "redirect:swagger-ui/index.html";
  }
}
