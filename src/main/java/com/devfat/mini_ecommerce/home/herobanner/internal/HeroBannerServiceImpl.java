package com.devfat.mini_ecommerce.home.herobanner.internal;

import com.devfat.mini_ecommerce.home.herobanner.HeroBannerService;
import com.devfat.mini_ecommerce.home.herobanner.dto.CreateHeroBannerRequestDto;
import com.devfat.mini_ecommerce.home.herobanner.dto.HeroBannerResponseDto;
import com.devfat.mini_ecommerce.home.herobanner.dto.UpdateHeroBannerRequestDto;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import com.devfat.mini_ecommerce.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class HeroBannerServiceImpl implements HeroBannerService {

    private final HeroBannerRepository heroBannerRepository;
    private final HeroBannerMapper heroBannerMapper;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public List<HeroBannerResponseDto> getAllActiveBanners() {
       return heroBannerRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(heroBannerMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<HeroBannerResponseDto> getAllBanners() {
        return heroBannerRepository.findAll()
                .stream()
                .map(heroBannerMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public HeroBannerResponseDto getById(Long id) {
        return heroBannerMapper.toResponseDto(heroBannerRepository.getReferenceById(id));
    }

    @Override
    @CacheEvict(value = "home:heroSlides", allEntries = true)
    @Transactional
    public HeroBannerResponseDto create(CreateHeroBannerRequestDto request) {
        if(request == null) throw new IllegalArgumentException("request is null");
        HeroBannerEntity heroBannerEntity = heroBannerMapper.toEntity(request);
        heroBannerRepository.save(heroBannerEntity);
        return heroBannerMapper.toResponseDto(heroBannerEntity);
    }

    @Override
    @CacheEvict(value = "home:heroSlides", allEntries = true)
    @Transactional
    public HeroBannerResponseDto update(Long id, UpdateHeroBannerRequestDto request) {
        HeroBannerEntity heroBannerEntity = heroBannerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
        if(request == null) throw new IllegalArgumentException("request is null");

        heroBannerMapper.updateEntityFromDto(request, heroBannerEntity);
        return heroBannerMapper.toResponseDto(heroBannerRepository.save(heroBannerEntity));
    }

    @Override
    @CacheEvict(value = "home:heroSlides", allEntries = true)
    @Transactional
    public void delete(Long id) {
        heroBannerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
        heroBannerRepository.deleteById(id);
    }

    @Override
    @CacheEvict(value = "home:heroSlides", allEntries = true)
    @Transactional
    public void toggleBannerActive(Long id) {
        HeroBannerEntity heroBannerEntity = heroBannerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));

        heroBannerEntity.setActive(!heroBannerEntity.isActive());
        heroBannerRepository.save(heroBannerEntity);
    }

    @Override
    @CacheEvict(value = "home:heroSlides", allEntries = true)
    @Transactional
    public HeroBannerResponseDto uploadBannerImage(Long id, MultipartFile file) {
        HeroBannerEntity heroBannerEntity = heroBannerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));

        if(file == null) throw new IllegalArgumentException("file is null");
        if(file.isEmpty()) throw new IllegalArgumentException("file is empty");

        String url = storageService.uploadFile(file, "bannerHeroImage", true);
        heroBannerEntity.setCardImageUrl(url);
        return heroBannerMapper.toResponseDto(heroBannerRepository.save(heroBannerEntity));
    }
}
