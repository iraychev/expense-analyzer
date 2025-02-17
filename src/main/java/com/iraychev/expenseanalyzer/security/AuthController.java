@RestController
@Slf4j
@Profile("!test")
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final AuthService authService;

    @PostMapping("/api/token")
    @Override
    public String token(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            log.debug("Authorization Header: {}", authHeader);

            return authService.authenticateAndGenerateToken(authHeader);
        } catch (Exception e) {
            log.error("Authentication failed", e);
            throw new UserNotFoundException("User Not Found");
        }
    }
}
