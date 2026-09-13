package com.devfat.mini_ecommerce.user.address.internal;

import com.devfat.mini_ecommerce.shared.exception.BadRequestException;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.user.address.AddressService;
import com.devfat.mini_ecommerce.user.address.dto.AddressResponseDto;
import com.devfat.mini_ecommerce.user.address.dto.ChangeDefaultAddressDto;
import com.devfat.mini_ecommerce.user.address.dto.CreateAddressRequestDto;
import com.devfat.mini_ecommerce.user.address.dto.UpdateAddressRequestDto;
import com.devfat.mini_ecommerce.user.address.exception.AddressAlreadyDefaultException;
import com.devfat.mini_ecommerce.user.address.exception.CannotDeleteDefaultAddressException;
import com.devfat.mini_ecommerce.user.address.exception.CannotDeleteOnlyAddressException;
import com.devfat.mini_ecommerce.user.internal.UserEntity;
import com.devfat.mini_ecommerce.user.internal.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final AddressMapper addressMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AddressResponseDto createAddress(Long userId, CreateAddressRequestDto createAddressRequestDto) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        boolean exists = addressRepository.existsByUserId(userId);
        boolean isNewAddressDefault;


        if (!exists) {
            // Trường hợp 1: Chưa có địa chỉ nào -> Bắt buộc là mặc định
            isNewAddressDefault = true;
        } else if (createAddressRequestDto.defaultAddress()) {
            // Trường hợp 2: Đã có địa chỉ và request muốn đặt làm mặc định
            Optional<UserAddressEntity> addressOld = addressRepository.findByUserIdAndDefaultAddressIsTrue(userId);
            if (addressOld.isPresent()) {
                UserAddressEntity userAddressEntity = addressOld.get();
                userAddressEntity.setDefaultAddress(false);
                addressRepository.save(userAddressEntity);
            }
            isNewAddressDefault = true;
        } else {
            // Trường hợp 3: Đã có địa chỉ và request không muốn làm mặc định
            isNewAddressDefault = false;
        }


        UserAddressEntity userAddressEntity = new UserAddressEntity();
        userAddressEntity.setRecipientName(createAddressRequestDto.recipientName());
        userAddressEntity.setPhone(createAddressRequestDto.phone());
        userAddressEntity.setProvince(createAddressRequestDto.province());
        userAddressEntity.setDistrict(createAddressRequestDto.district());
        userAddressEntity.setWard(createAddressRequestDto.ward());
        userAddressEntity.setAddressDetail(createAddressRequestDto.addressDetail());
        userAddressEntity.setTag(createAddressRequestDto.tag());
        userAddressEntity.setDefaultAddress(isNewAddressDefault);
        userAddressEntity.setUser(user);
        addressRepository.save(userAddressEntity);
        return addressMapper.toResponseDto(userAddressEntity);
    }

    @Override
    @Transactional
    public AddressResponseDto updateAddress(Long userId, Long addressId, UpdateAddressRequestDto updateAddressRequestDto) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        UserAddressEntity userAddress = addressRepository.findById(addressId).orElseThrow(() -> new ResourceNotFoundException("Address not found!"));

        if (!userAddress.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied. This address does not belong to you!");
        }

        addressMapper.updateEntityFromDto(updateAddressRequestDto, userAddress);
        return addressMapper.toResponseDto(addressRepository.save(userAddress));
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        UserAddressEntity userAddressEntity = addressRepository.findById(addressId).orElseThrow(() -> new ResourceNotFoundException("Address not found!"));

        if (!userAddressEntity.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied. This address does not belong to you!");
        }

        List<UserAddressEntity> userAddressOld = addressRepository.findByUserId(userId);

        if (userAddressOld.size() == 1) {
            throw new CannotDeleteOnlyAddressException("Cannot delete the only shipping address. You must have at least one address.");
        }

        if (userAddressEntity.isDefaultAddress()) {
            throw new CannotDeleteDefaultAddressException("Cannot delete the default address. Please set another address as default first.");
        }

        addressRepository.delete(userAddressEntity);
    }

    @Override
    @Transactional
    public void changeDefaultAddress(Long userId, Long addressId, ChangeDefaultAddressDto changeDefaultAddressDto) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));
        UserAddressEntity userAddress = addressRepository.findById(addressId).orElseThrow(() -> new ResourceNotFoundException("Address not found!"));

        if (!userAddress.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Access denied. This address does not belong to you!");
        }

        if (!changeDefaultAddressDto.defaultAddress()) {
            throw new BadRequestException("Cannot disable default address status. Please set another address as default instead.");
        }

        if (userAddress.isDefaultAddress()) {
            throw new AddressAlreadyDefaultException("This address is already set as the default address.");
        }

        Optional<UserAddressEntity> addressOld = addressRepository.findByUserIdAndDefaultAddressIsTrue(userId);
        if (addressOld.isPresent()) {
            UserAddressEntity oldAddress = addressOld.get();
            oldAddress.setDefaultAddress(false);
            addressRepository.save(oldAddress);
        }

        userAddress.setDefaultAddress(true);
        addressRepository.save(userAddress);
    }


    @Override
    @Transactional(readOnly = true)
    public List<AddressResponseDto> getAddressList(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found!"));

        return addressRepository.findByUserId(userId).stream()
                .map(addressMapper::toResponseDto)
                .collect(Collectors.toList());
    }
}
