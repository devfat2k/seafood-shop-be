package com.devfat.mini_ecommerce.home.dailyarrival.internal;

import com.devfat.mini_ecommerce.home.dailyarrival.DailyArrivalService;
import com.devfat.mini_ecommerce.home.dailyarrival.dto.CreateDailyArrivalRequestDto;
import com.devfat.mini_ecommerce.home.dailyarrival.dto.DailyArrivalResponseDto;
import com.devfat.mini_ecommerce.home.dailyarrival.dto.UpdateDailyArrivalRequestDto;
import com.devfat.mini_ecommerce.product.internal.ProductEntity;
import com.devfat.mini_ecommerce.product.internal.ProductRepository;
import com.devfat.mini_ecommerce.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyArrivalServiceImpl implements DailyArrivalService {

    private final DailyArrivalRepository dailyArrivalRepository;
    private final DailyArrivalMapper dailyArrivalMapper;
    private final ProductRepository productRepository;

    @Override
    public List<DailyArrivalResponseDto> getByDate(LocalDate date) {
        return dailyArrivalRepository.findByArrivalDateWithProduct(date)
                .stream().map(dailyArrivalMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "home:dailyArrivals", allEntries = true)
    public DailyArrivalResponseDto create(CreateDailyArrivalRequestDto request) {
        if(request == null) throw new IllegalArgumentException("Request is null");

        ProductEntity product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found with id: " + request.productId()));

        DailyArrivalEntity entity = dailyArrivalMapper.toEntity(request);
        entity.setProduct(product);

        return dailyArrivalMapper.toResponseDto(dailyArrivalRepository.save(entity));
    }

    @Override
    @Transactional
    @CacheEvict(value = "home:dailyArrivals", allEntries = true)
    public DailyArrivalResponseDto update(Long id, UpdateDailyArrivalRequestDto request) {
        if(request == null) throw new IllegalArgumentException("Request is null");

        DailyArrivalEntity dailyArrival = dailyArrivalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Not found!"));

        dailyArrivalMapper.updateEntityFromDto(request, dailyArrival);
        return dailyArrivalMapper.toResponseDto(dailyArrivalRepository.save(dailyArrival));
    }

    @Override
    @Transactional
    @CacheEvict(value = "home:dailyArrivals", allEntries = true)
    public void delete(Long id) {
        if(id == null) throw new IllegalArgumentException("id is null");

        dailyArrivalRepository.deleteById(id);
    }
}
