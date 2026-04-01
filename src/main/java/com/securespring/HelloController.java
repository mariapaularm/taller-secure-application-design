package com.securespring;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/api/hello")
	public String hello(Principal principal) {
	    return "¡Hola, " + principal.getName() + "! Bienvenido a la aplicación segura.";
	}
}