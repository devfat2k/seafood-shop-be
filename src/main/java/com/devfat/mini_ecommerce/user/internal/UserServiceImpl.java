package com.devfat.mini_ecommerce.user.internal;

import com.devfat.mini_ecommerce.auth.OtpPurpose;
import com.devfat.mini_ecommerce.auth.OtpService;
import com.devfat.mini_ecommerce.auth.internal.RefreshTokenEntity;
import com.devfat.mini_ecommerce.auth.internal.RefreshTokenRepository;
import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.storage.StorageService;
import com.devfat.mini_ecommerce.user.UserService;
import com.devfat.mini_ecommerce.user.dto.ChangePasswordRequestDto;
import com.devfat.mini_ecommerce.user.dto.UpdateProfileRequestDto;
import com.devfat.mini_ecommerce.user.dto.UserResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private final StorageService storageService;
    private final OtpService otpService;
    private final UserMapper userMapper;
    private final UserPermissionCacheService userPermissionCacheService;


    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getMe(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDto);
    }
    

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequestDto dto) {
        String oldPassword = dto.oldPassword().trim();
        String newPassword = dto.newPassword().trim();

        String hashedNewPassword = passwordEncoder.encode(newPassword);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!(passwordEncoder.matches(oldPassword, user.getPassword()))) {
            throw new BadRequestException("Old password does not match");
        }

        user.setPassword(hashedNewPassword);
    }

    @Override
    @Transactional
    public UserResponseDto updateProfile(Long id, UpdateProfileRequestDto updateProfileRequestDto) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if(updateProfileRequestDto.phoneNumber() != null) {
            user.setPhoneNumber(updateProfileRequestDto.phoneNumber());
        }
        if(updateProfileRequestDto.fullName() != null) {
            user.setFullName(updateProfileRequestDto.fullName());
        }
        userRepository.save(user);

        return userMapper.toResponseDto(user);
    }

    @Override
    @Transactional
    public void updateStatusUser(Long idInQuery, Long idInToken, Boolean isActive) {
        UserEntity user = userRepository.findById(idInQuery).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if(idInToken.equals(user.getId())) {
            throw new BadRequestException("User cannot change status for me!");
        }

        if(user.isActive() == isActive) {
            throw new BadRequestException("User cannot change status current!");
        }

        if(!isActive) {
            List<RefreshTokenEntity> token = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
            token.forEach(tokenEntity -> {
                tokenEntity.setRevoked(true);
            });
            refreshTokenRepository.saveAll(token);
        }
        user.setActive(isActive);
        userRepository.save(user);
        userPermissionCacheService.evictUserPermissions(idInQuery);
    }

    @Override
    @Transactional
    public UserResponseDto uploadUserImage(Long id, MultipartFile file) {
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        String url = storageService.uploadFile(file, "UserImage", true);
        user.setAvatarUrl(url);
        userRepository.save(user);
        return userMapper.toResponseDto(user);
    }

    @Override
    @Transactional
    public void requestChangePasswordOtp(Long currentUserId) {
        UserEntity user = userRepository.findById(currentUserId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        otpService.generateAndSendOtp(user, OtpPurpose.CHANGE_PASSWORD_CONFIRMATION);
    }
}
