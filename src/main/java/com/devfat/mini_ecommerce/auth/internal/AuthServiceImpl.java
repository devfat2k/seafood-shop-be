package com.devfat.mini_ecommerce.auth.internal;

import com.devfat.mini_ecommerce.auth.AuthService;
import com.devfat.mini_ecommerce.auth.OtpPurpose;
import com.devfat.mini_ecommerce.auth.OtpService;
import com.devfat.mini_ecommerce.auth.dto.*;
import com.devfat.mini_ecommerce.auth.exception.InvalidRefreshTokenException;
import com.devfat.mini_ecommerce.auth.exception.ResendCooldownException;
import com.devfat.mini_ecommerce.shared.exception.AccountNotVerifiedException;
import com.devfat.mini_ecommerce.shared.exception.DuplicateResourceException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.shared.security.JwtProvider;
import com.devfat.mini_ecommerce.shared.security.UserPrincipal;
import com.devfat.mini_ecommerce.user.dto.UserResponseDto;
import com.devfat.mini_ecommerce.user.internal.*;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    private final JwtProvider jwtProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;

    private final OtpService otpService;
    private final UserMapper userMapper;

    @Value("${app.jwt.refresh-expiration-days}")
    private long refreshTokenExpirationDays;


    private String hashRefreshToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Server error: Missing SHA-256 algorithm", e);
        }
    }

    @Override
    @Transactional
    public UserResponseDto register(RegisterRequestDto registerRequestDto) {
        String email = registerRequestDto.email().trim().toLowerCase(Locale.ROOT);
        String phone = registerRequestDto.phoneNumber().trim(); // KHÔNG ĐUỌC toLowerCase vì sẽ lấy sai khi so sánh với pw dưới db

        boolean presentEmail = userRepository.findByEmail(email).isPresent();
        boolean presentPhone = userRepository.findByPhoneNumber(phone).isPresent();
        if (presentEmail) throw new DuplicateResourceException("User already exists with email: " + email);
        if (presentPhone) throw new DuplicateResourceException("User already exists with phone number: " + phone);

        String rawPassword = registerRequestDto.password();
        String hashedPassword = passwordEncoder.encode(rawPassword);

        UserEntity newUser = new UserEntity();
        newUser.setFullName(registerRequestDto.fullName());
        newUser.setEmail(email);
        newUser.setPhoneNumber(phone);

        RoleEntity defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.findByName("USER")
                        .orElseGet(() -> roleRepository.findByName("CUSTOMER").orElse(null)));
        if (defaultRole != null) {
            newUser.getRoles().add(defaultRole);
        }

        newUser.setEmailVerified(false);
        newUser.setActive(true); // Mặc định tạo sẽ đang hoạt động - Nếu có thay update cờ này về false
        newUser.setPassword(hashedPassword);

        UserEntity savedUser = userRepository.save(newUser);

        otpService.generateAndSendOtp(
                savedUser,
                OtpPurpose.REGISTER_VERIFICATION
        );

        return userMapper.toResponseDto(savedUser);
    }


    /**
     * 1. Tạo UsernamePasswordAuthenticationToken(email, password) — token "thô" chưa xác thực
     * 2. Gọi authenticationManager.authenticate(token đó)
     *    -> Bên trong Spring tự gọi CustomUserDetailsService (B1) + PasswordEncoder (B2)
     *    -> Nếu sai -> tự động ném BadCredentialsException (bạn KHÔNG tự viết if/else so sánh)
     * 3. Lấy Authentication trả về, cast/lấy Principal ra thành UserPrincipal
     * 4. Gọi jwtProvider.generateToken(userPrincipal) (C3) -> nhận accessToken
     * 5. Build AuthResponseDto trả về
     *
     * login() hiện tại (C4) chạy tới bước generateToken() cho accessToken như cũ
     *    → THÊM MỚI:
     *    1. Sinh refresh token: chuỗi ngẫu nhiên, KHÔNG cần cấu trúc JWT
     *       (dùng UUID.randomUUID().toString() hoặc SecureRandom — tự chọn 1 cách)
     *    2. Hash chuỗi đó (dùng LẠI PasswordEncoder đã có sẵn từ B2 — tự quyết định,
     *       xem lưu ý bên dưới về BCrypt vs SHA-256)
     *    3. Build RefreshTokenEntity: user, tokenHash (bản đã hash), expiresAt (now + X ngày),
     *       revoked = false
     *    4. Lưu qua RefreshTokenRepository.save()
     *    5. Response trả về CẢ 2: accessToken (như cũ) VÀ refreshToken (bản RAW, chưa hash)
     */

    @Override
    @Transactional
    public AuthResponseDto login(LoginRequestDto loginRequestDto) {
        String email = loginRequestDto.email().trim().toLowerCase(Locale.ROOT);
        String password = loginRequestDto.password().trim();

        UsernamePasswordAuthenticationToken tokenRequest = new UsernamePasswordAuthenticationToken(email, password);
        Authentication lastestResult = authenticationManager.authenticate(tokenRequest);
        UserPrincipal userPrincipal = (UserPrincipal) lastestResult.getPrincipal();

        if (!userPrincipal.isEmailVerified()) {
            throw new AccountNotVerifiedException("Account not verified! Please check your email address and try again.");
        }

        String accessToken = jwtProvider.generateToken(userPrincipal);


        refreshTokenRepository.deleteExpiredOrRevokedByUserId(userPrincipal.getUserId(), LocalDateTime.now()); // clear

        String refreshToken = refreshTokenGenerator.generateRefreshToken();
        String tokenHash = hashRefreshToken(refreshToken);
        LocalDateTime now = LocalDateTime.now(); // Lấy thời điểm hiện tại lúc đăng nhập
        LocalDateTime expirationDate = now.plusDays(refreshTokenExpirationDays); // Cộng thêm số ngày đang cài đăt là 7 để hết hạn refresh_token

//        UserEntity user = userRepository.findById(userPrincipal.getUserId()).orElseThrow(() -> new ResourceNotFoundException("User id not found")); -- Cách 1 lấy user
        UserEntity user = userRepository.getReferenceById(userPrincipal.getUserId()); // -- Cách 2L Tối ưu đươc 1 câu lệnh query nhưng không báo lỗi ở dòng này như findById

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(tokenHash);
        refreshTokenEntity.setExpireAt(expirationDate);
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);


        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProvider.getExpirationMs())
                .build();
    }

    /**
     * 1. Nhận refreshToken (raw) từ Client
     * 2. Hash lại chuỗi đó (CÙNG cách đã hash lúc lưu ở E3)
     * 3. Tìm trong RefreshTokenRepository.findByTokenHash(hash đó)
     *    -> Không tìm thấy -> throw exception (401)
     * 4. Kiểm tra: entity.isRevoked() == false VÀ entity.getExpiresAt() chưa qua
     *    -> Sai điều kiện nào -> throw exception (401)
     * 5. Lấy user từ entity.getUser() -> bọc thành UserPrincipal (giống cách CustomUserDetailsService làm)
     * 6. jwtProvider.generateToken(userPrincipal) -> accessToken MỚI
     * 7. Trả về accessToken mới (refreshToken giữ nguyên, KHÔNG đổi ở bước này — Token Rotation là việc của E6, không bắt buộc)
     */
    @Override
    @Transactional
    public RefreshTokenResponseDto refreshToken(RefreshTokenRequestDto refreshTokenRequestDto) {
      String tokenHash = hashRefreshToken(refreshTokenRequestDto.refreshToken());
      String genericErrorMessage = "Invalid or expired refresh token";
      RefreshTokenEntity refreshTokenEntity = refreshTokenRepository.findByTokenHash(tokenHash)
              .orElseThrow(() -> new InvalidRefreshTokenException(genericErrorMessage));

        if(refreshTokenEntity.isRevoked()) {
            throw new InvalidRefreshTokenException(genericErrorMessage);
        }

        if(refreshTokenEntity.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException(genericErrorMessage);
        }
        UserEntity user = refreshTokenEntity.getUser();
        UserPrincipal userPrincipal = com.devfat.mini_ecommerce.shared.security.UserPrincipalFactory.fromEntity(user);
        String accessToken = jwtProvider.generateToken(userPrincipal);
        return RefreshTokenResponseDto.builder()
                .accessToken(accessToken)
                .build();
    }

    /**
     * 1. Nhận refreshToken (raw) từ Client
     * 2. Hash lại (CÙNG cách E3/E4 đã dùng — SHA-256)
     * 3. Tìm trong RefreshTokenRepository.findByTokenHash(hash đó)
     *    -> Không tìm thấy -> có 2 lựa chọn thiết kế (xem lưu ý dưới)
     * 4. Set revoked = true, save lại
     * 5. Trả về response rỗng/thành công đơn giản (không cần trả data gì đặc biệt)
     */
    @Override
    @Transactional
    public void logout(RefreshTokenRequestDto refreshTokenRequestDto) {
        String tokenHash = hashRefreshToken(refreshTokenRequestDto.refreshToken());
        RefreshTokenEntity refreshTokenEntity = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null);

        if (refreshTokenEntity != null) {
            refreshTokenEntity.setRevoked(true);
            refreshTokenRepository.save(refreshTokenEntity);
        }
    }

    @Override
    @Transactional
    public VerifyOtpResponseDto verifyOtp(VerifyOtpRequestDto dto) {
        UserEntity user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        otpService.verifyOtp(user.getId(), dto.purpose(), dto.otpCode());

        return switch (dto.purpose()) {
            case REGISTER_VERIFICATION -> handleRegisterVerified(user);
            case RESET_PASSWORD -> handleResetPasswordVerified(user);
            case CHANGE_PASSWORD_CONFIRMATION -> handleChangePasswordVerified(user);
        };
    }


    @Override
    public void forgotPassword(ForgotPasswordRequestDto dto) {
        userRepository.findByEmail(dto.email())
                .ifPresent(user -> otpService.generateAndSendOtp(user, OtpPurpose.RESET_PASSWORD));
    }


    @Override
    public ResendOtpResponseDto resendOtp(ResendOtpRequestDto dto) {
        UserEntity user = userRepository.findByEmail(dto.email())
                .orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        if (!otpService.resetOtp(user.getId(), dto.purpose())) {
            throw new ResendCooldownException("Please try again later!");
        }

        otpService.generateAndSendOtp(user, dto.purpose());

        return ResendOtpResponseDto.builder()
                .message("New verification code has been sent!")
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDto dto) {
        String password = dto.newPassword();
        String hashedPassword = passwordEncoder.encode(password);

        Long userId = jwtProvider.validateActionTokenAndGetUserId(dto.actionToken(), OtpPurpose.RESET_PASSWORD.name());

        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        user.setPassword(hashedPassword);
    }


    private VerifyOtpResponseDto handleRegisterVerified(UserEntity user) {
        user.setEmailVerified(true);
        userRepository.save(user);

        UserPrincipal userPrincipal = com.devfat.mini_ecommerce.shared.security.UserPrincipalFactory.fromEntity(user);
        String accessToken = jwtProvider.generateToken(userPrincipal);

        refreshTokenRepository.deleteExpiredOrRevokedByUserId(user.getId(), LocalDateTime.now());

        String refreshToken = refreshTokenGenerator.generateRefreshToken();
        String tokenHash = hashRefreshToken(refreshToken);
        LocalDateTime expirationDate = LocalDateTime.now().plusDays(refreshTokenExpirationDays);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setTokenHash(tokenHash);
        refreshTokenEntity.setExpireAt(expirationDate);
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);

        return VerifyOtpResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private VerifyOtpResponseDto handleResetPasswordVerified(UserEntity user) {
        String resetTicket = jwtProvider.generateActionToken(user.getId(), "RESET_PASSWORD", Duration.ofMinutes(10));
        return VerifyOtpResponseDto.builder().actionToken(resetTicket).build();
    }

    private VerifyOtpResponseDto handleChangePasswordVerified(UserEntity user) {
        String confirmTicket = jwtProvider.generateActionToken(user.getId(), "CHANGE_PASSWORD", Duration.ofMinutes(10));
        return VerifyOtpResponseDto.builder().actionToken(confirmTicket).build();
    }
}
