package com.devfat.mini_ecommerce.user;

import com.devfat.mini_ecommerce.user.address.dto.ChangeDefaultAddressDto;
import com.devfat.mini_ecommerce.user.address.dto.UpdateAddressRequestDto;
import com.devfat.mini_ecommerce.user.address.internal.AddressMapper;
import com.devfat.mini_ecommerce.user.address.internal.AddressRepository;
import com.devfat.mini_ecommerce.user.address.internal.AddressServiceImpl;
import com.devfat.mini_ecommerce.user.address.internal.UserAddressEntity;
import com.devfat.mini_ecommerce.user.internal.UserEntity;
import com.devfat.mini_ecommerce.user.internal.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AddressServiceImplTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private AddressMapper addressMapper;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressServiceImpl addressService;

    private UserEntity owner;
    private UserEntity otherUser;
    private UserAddressEntity address;

    @BeforeEach
    void setUp() {
        owner = new UserEntity();
        owner.setId(1L);

        otherUser = new UserEntity();
        otherUser.setId(2L);

        address = new UserAddressEntity();
        address.setId(100L);
        address.setUser(owner);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when updating another user's address (IDOR)")
    void shouldThrowWhenUpdatingAnotherUserAddress() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));

        UpdateAddressRequestDto dto = new UpdateAddressRequestDto(
                "John", "0912345678", "City", "District", "Ward", "Street", false, "HOME"
        );

        assertThrows(AccessDeniedException.class, () ->
                addressService.updateAddress(2L, 100L, dto)
        );
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when deleting another user's address (IDOR)")
    void shouldThrowWhenDeletingAnotherUserAddress() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));

        assertThrows(AccessDeniedException.class, () ->
                addressService.deleteAddress(2L, 100L)
        );
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when changing default of another user's address (IDOR)")
    void shouldThrowWhenChangingDefaultOfAnotherUserAddress() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(addressRepository.findById(100L)).thenReturn(Optional.of(address));

        ChangeDefaultAddressDto dto = new ChangeDefaultAddressDto(true);

        assertThrows(AccessDeniedException.class, () ->
                addressService.changeDefaultAddress(2L, 100L, dto)
        );
    }
}
