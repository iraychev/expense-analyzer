@Service
@RequiredArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public String authenticateAndGenerateToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(("Basic "))) {
            throw new UserNotFoundException("Missing or invalid Authorization header");
        }

        String base64Credentials = authHeader.substring("Basic ".length());
        String credentials = new String(Base64.getDecoder().decode(base64Credentials));
        String[] values = credentials.split(":", 2);
        String username = values[0];
        String password = values[1];
        username = username.toLowerCase(Locale.ROOT);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        if (!authentication.isAuthenticated()) {
            throw new UserNotFoundException("Invalid user request");
        }
        return tokenService.generateToken(authentication);
    }
}
