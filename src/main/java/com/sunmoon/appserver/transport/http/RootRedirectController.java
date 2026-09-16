package com.sunmoon.appserver.transport.http;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// See sun-moon-java-platform-order's class of the same name for why
// this exists.
@Controller
public class RootRedirectController {

    @GetMapping("/")
    public String redirectToSwaggerUi() {
        return "redirect:/swagger-ui/index.html";
    }
}
