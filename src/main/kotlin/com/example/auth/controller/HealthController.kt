package auth_api.controller


import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class HealthController {
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "ok",
            "service" to "auth-api"
        )
    }
}